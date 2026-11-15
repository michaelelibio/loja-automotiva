package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.order.entity.*;
import com.garage.garageapi.order.fulfillment.*;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.product.repository.ProductVariantRepository;
import com.garage.garageapi.shared.exception.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Exception workflow for the current API's missing destination-level freight/tax quote. Never creates or pays. */
@Service
public class AliExpressDispatchQuoteService {
    private final OrderRepository orders;
    private final OrderFulfillmentRepository fulfillments;
    private final ProductVariantRepository variants;
    private final AliExpressDispatchValidation validation;
    private final AliExpressTokenService tokens;
    private final TransactionTemplate tx;
    public AliExpressDispatchQuoteService(OrderRepository orders, OrderFulfillmentRepository fulfillments,
            ProductVariantRepository variants, AliExpressDispatchValidation validation, AliExpressTokenService tokens,
            PlatformTransactionManager manager) {
        this.orders=orders;this.fulfillments=fulfillments;this.variants=variants;this.validation=validation;this.tokens=tokens;
        tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public record Line(@NotBlank String skuId, @NotNull @DecimalMin("0.01") @Digits(integer=8,fraction=2) BigDecimal unitPrice,
                       @NotBlank @Size(max=100) String logisticsService) { }
    public record Request(@NotBlank @Pattern(regexp="[a-f0-9]{64}") String contextDigest,
            @NotEmpty @Size(max=100) List<@Valid Line> lines,
            @NotNull @DecimalMin("0.00") @Digits(integer=8,fraction=2) BigDecimal shippingTotal,
            @NotNull @DecimalMin("0.00") @Digits(integer=8,fraction=2) BigDecimal taxTotal,
            @NotNull @DecimalMin("0.01") @Digits(integer=8,fraction=2) BigDecimal total,
            @NotNull Instant validUntil, @NotBlank @Size(max=120) String evidenceReference,
            @AssertTrue boolean fullDestinationQuoteConfirmed, @AssertTrue boolean noExistingSupplierOrderConfirmed,
            boolean acceptCostAboveSale) { }
    public void approve(Long id, Request request, String actor) {
        var access=tokens.dispatchAccess();
        String buyerHash=access.buyerHash();
        var quote=tx.execute(s->build(id,request,actor,buyerHash));
        validation.verifyProducts(quote,access.token(),access.buyerHash());
        tx.executeWithoutResult(s->{
            var order=orders.findByIdForLifecycleUpdate(id).orElseThrow(()->new ResourceNotFoundException("Pedido não encontrado."));
            require(order.quoteContextDigest().equals(quote.contextDigest()) && Instant.now().isBefore(quote.validUntil()),"Pedido alterado ou cotação expirada durante a validação.");
            var row=fulfillments.findByOrderIdForUpdate(id).orElseThrow(()->new ResourceConflictException("Registro do fornecedor ausente."));
            row.approveDispatch(quote);fulfillments.flush();
        });
    }
    private AliExpressDispatchQuote build(Long id,Request request,String actor,String buyerHash) {
        var order=orders.findByIdWithItems(id).orElseThrow(()->new ResourceNotFoundException("Pedido não encontrado."));
        require(order.getStatus()==OrderStatus.PENDING_PAYMENT || order.getStatus()==OrderStatus.PAID,"Pedido não pode receber cotação de criação.");
        require(request.fullDestinationQuoteConfirmed() && request.noExistingSupplierOrderConfirmed(),"Confirme a cotação integral para o destinatário e a ausência de pedido já criado no AliExpress.");
        require(request.evidenceReference()!=null && !request.evidenceReference().isBlank(),"Referência da cotação integral verificada no fornecedor é obrigatória.");
        require(order.quoteContextDigest().equals(request.contextDigest()),"Pedido alterado. Atualize o ADM e confira o destinatário e os itens.");
        var now=Instant.now();
        require(request.validUntil()!=null && request.validUntil().isAfter(now) && !request.validUntil().isAfter(now.plusSeconds(1800)),"Validade deve ser futura e de no máximo 30 minutos.");
        require(com.garage.garageapi.order.recipient.AliExpressRecipientData.status(order).complete(),"Dados do destinatário incompletos.");
        require(request.lines()!=null && request.lines().size()==order.getItems().size(),"Informe a cotação de todas as variantes, sem duplicidade.");
        Set<String> used=new HashSet<>();var lines=new ArrayList<AliExpressDispatchQuote.Line>();BigDecimal productTotal=BigDecimal.ZERO;
        for(var item:order.getItems()) {
            var v=variants.findById(item.getProductVariantId()).orElseThrow(()->new ResourceConflictException("Variante não encontrada."));
            require("ALIEXPRESS".equals(item.getSupplier()) && "ALIEXPRESS".equals(v.getSupplier()) && Objects.equals(item.getSupplierProductId(),v.getSupplierProductId())
                    && Objects.equals(item.getSupplierVariantId(),v.getSupplierVariantId()) && Objects.equals(item.getProductId(),v.getProduct().getId()),"Produto/variante não corresponde ao fornecedor.");
            var line=request.lines().stream().filter(l->Objects.equals(l.skuId(),item.getSupplierVariantId())).findFirst().orElseThrow(()->new ResourceConflictException("Variante sem cotação."));
            require(used.add(line.skuId()) && line.unitPrice()!=null && line.unitPrice().signum()>0 && line.logisticsService()!=null && !line.logisticsService().isBlank(),"Cotação de variante inválida ou duplicada.");
            lines.add(new AliExpressDispatchQuote.Line(item.getSupplierProductId(),line.skuId(),v.getRawVariantKey(),item.getQuantity(),line.unitPrice(),line.logisticsService()));
            productTotal=productTotal.add(line.unitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        require(request.shippingTotal()!=null && request.shippingTotal().signum()>=0 && request.taxTotal()!=null && request.taxTotal().signum()>=0
                && request.total()!=null && productTotal.add(request.shippingTotal()).add(request.taxTotal()).compareTo(request.total())==0,"Total deve incluir produto, frete e impostos explicitamente confirmados em BRL.");
        // Recording a quotation is optional; loss acceptance is never a creation gate.
        return new AliExpressDispatchQuote(order.quoteContextDigest(),buyerHash,validation.appHash(),List.copyOf(lines),productTotal,
                request.shippingTotal(),request.taxTotal(),request.total(),now,request.validUntil(),actor,request.evidenceReference());
    }
    private static void require(boolean value,String message) { if(!value)throw new ResourceConflictException(message); }
}
