package com.garage.garageapi.order.quote;

import com.garage.garageapi.order.entity.*;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.order.fulfillment.*;
import com.garage.garageapi.payment.repository.PaymentRepository;
import com.garage.garageapi.payment.entity.PaymentStatus;
import com.garage.garageapi.shared.exception.*;
import com.garage.garageapi.shipping.provider.ShippingProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@Transactional
public class AssistedQuoteService {
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final OrderFulfillmentRepository fulfillments;
    public AssistedQuoteService(OrderRepository orders, PaymentRepository payments, OrderFulfillmentRepository fulfillments) {
        this.orders=orders;this.payments=payments;this.fulfillments=fulfillments;
    }
    public record Leg(@NotBlank @Pattern(regexp="[A-Za-z0-9_.-]{1,50}") String code,
            @NotBlank @Size(max=120) String name, @NotBlank @Pattern(regexp="[A-Z]{2}") String originCountry,
            @NotNull @DecimalMin("0.00") @Digits(integer=8,fraction=2) BigDecimal shippingBrl,
            @Min(1) @Max(365) int estimatedDays, @NotEmpty List<@NotBlank @Pattern(regexp="[0-9]{1,30}") String> skuIds) {}
    public record Confirm(@NotNull @DecimalMin("0.01") @Digits(integer=8,fraction=2) BigDecimal supplierTotalBrl,
            @NotNull @DecimalMin("0.01") @Digits(integer=8,fraction=2) BigDecimal expectedTotal,
            @NotNull Instant validUntil, @NotBlank @Pattern(regexp="[A-Za-z0-9_.:/-]{1,150}") String evidenceReference,
            @AssertTrue boolean fullAddressAndTaxesChecked, @NotEmpty @Size(max=100) List<@NotNull @Valid Leg> legs) {}
    public record Accept(@Min(1) int revision, @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal expectedTotal) {}
    public record Recheck(@NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal supplierTotalBrl,
            boolean sameAddressItemsAndMethods) {}
    public record Reference(@NotBlank @Pattern(regexp="[A-Za-z0-9_.:/-]{1,150}") String reference) {}
    public record ManualCompletion(@NotEmpty @Size(max=100) List<@Pattern(regexp="[0-9]{1,30}") String> supplierOrderIds) {}

    public Order confirm(Long id, Confirm request, String admin) {
        Order order=locked(id);
        require(order.getAssistedQuote()!=null && "WAITING".equals(order.getAssistedQuote().state()),"Cotação já confirmada ou pedido não assistido.");
        require(order.getStatus()==OrderStatus.PENDING_PAYMENT,"Pedido indisponível para cotação.");
        Instant now=Instant.now();
        require(request.validUntil().isAfter(now) && !request.validUntil().isAfter(now.plus(Duration.ofHours(24))),"Validade deve ser futura, de até 24 horas.");
        require(request.fullAddressAndTaxesChecked(),"Confirme endereço completo, preço, impostos e frete no AliExpress sem criar pedido.");
        Set<String> expected=new HashSet<>();
        for (var item:order.getItems()) {
            require("ALIEXPRESS".equalsIgnoreCase(item.getSupplier()) && item.getSupplierVariantId()!=null,"Pedido deve conter somente variantes AliExpress.");
            require(expected.add(item.getSupplierVariantId()),"SKU repetido entre produtos: revisão manual necessária.");
        }
        Set<String> actual=new HashSet<>();
        List<ShippingProvider.Leg> legs=new ArrayList<>();
        for(var leg:request.legs()) {
            require(Arrays.asList(Locale.getISOCountries()).contains(leg.originCountry()),"País de origem inválido.");
            for(String sku:leg.skuIds()) require(actual.add(sku),"SKU em mais de uma remessa.");
            legs.add(new ShippingProvider.Leg("ALIEXPRESS",leg.code(),leg.name(),leg.originCountry(),leg.shippingBrl(),"BRL",leg.shippingBrl(),leg.estimatedDays(),List.copyOf(leg.skuIds())));
        }
        require(actual.equals(expected),"As remessas devem cobrir exatamente todos os SKUs do pedido.");
        BigDecimal shipping=legs.stream().map(ShippingProvider.Leg::priceBrl).reduce(BigDecimal.ZERO,BigDecimal::add);
        require(order.getSubtotal().add(shipping).compareTo(request.expectedTotal())==0,"Total divergente: confira subtotal e frete.");
        require(request.supplierTotalBrl().compareTo(request.expectedTotal())<=0,"Custo completo supera o total da venda. Revise o catálogo e solicite novo pedido.");
        order.confirmAssistedQuote(new AssistedQuote("CONFIRMED",1,now,request.validUntil(),null,
                request.supplierTotalBrl(),request.evidenceReference(),admin,null,null),legs);
        return order;
    }
    public Order accept(Long id,Long userId,Accept request) {
        Order order=orders.findByIdAndUserIdForUpdate(id,userId).orElseThrow(()->new ResourceNotFoundException("Pedido não encontrado."));
        var quote=order.getAssistedQuote();
        require(quote!=null && order.getStatus()==OrderStatus.PENDING_PAYMENT
                && ("CONFIRMED".equals(quote.state()) || "ACCEPTED".equals(quote.state())),"Aguarde a confirmação administrativa.");
        order.requireQuoteContextUnchanged();
        require(Instant.now().isBefore(quote.validUntil()),"Cotação expirada. Solicite um novo pedido.");
        require(quote.revision()==request.revision() && order.getTotal().compareTo(request.expectedTotal())==0,"O total mudou. Confira a cotação antes de aceitar.");
        if (!"ACCEPTED".equals(quote.state())) order.updateAssistedQuote(quote.accept());
        return order;
    }
    public Order recheck(Long id,Recheck request,String admin) {
        Order order=locked(id);requirePaid(order);
        var quote=order.getAssistedQuote();
        require(quote!=null && "ACCEPTED".equals(quote.state()),"Pedido bloqueado para encaminhamento.");
        order.updateAssistedQuote(quote.rechecked(request.supplierTotalBrl(),request.sameAddressItemsAndMethods() && quote.supplierTotalBrl().compareTo(request.supplierTotalBrl())==0,admin));
        return order; // Persist the hold even on a price mismatch; never roll it back with an exception.
    }
    public Order refund(Long id,Reference request) {
        Order order=locked(id);requirePaid(order);
        var quote=order.getAssistedQuote();
        require(quote!=null && "REFUND_REQUIRED".equals(quote.state()),"Não há reembolso pendente.");
        order.updateAssistedQuote(quote.refunded(request.reference()));
        order.cancelAfterRefund();
        return order; // Manual refund receipt; this endpoint never moves money.
    }
    public Order claimManual(Long id) {
        Order order=locked(id);requirePaid(order);order.requireAssistedDispatch();
        require(order.getAssistedQuote()!=null,"Pedido não assistido.");
        var f=fulfillments.findByOrderIdForUpdate(id).orElseThrow();
        f.beginManualAliExpress();
        return order;
    }
    public Order completeManual(Long id,ManualCompletion request) {
        Order order=locked(id);requirePaid(order);order.requireAssistedDispatch();
        var f=fulfillments.findByOrderIdForUpdate(id).orElseThrow();
        f.finishManualAliExpress(request.supplierOrderIds());
        return order;
    }
    public record Recipient(String cpf, java.time.LocalDate birthDate, String phone) {
        @Override public String toString() { return "Recipient[REDACTED]"; }
    }
    public Recipient recipient(Long id) {
        Order order=locked(id);requirePaid(order);
        require(order.getAssistedQuote()!=null,"Pedido não assistido.");
        return new Recipient(order.getRecipientCpf(),order.getRecipientBirthDate(),order.getRecipientPhone());
    }
    public AssistedQuote audit(Long id) { return locked(id).getAssistedQuote(); }
    private Order locked(Long id) { return orders.findByIdForLifecycleUpdate(id).orElseThrow(()->new ResourceNotFoundException("Pedido não encontrado.")); }
    private void requirePaid(Order order) {
        require((order.getStatus()==OrderStatus.PAID || order.getStatus()==OrderStatus.PROCESSING)
            && payments.findFirstByOrderIdAndStatusOrderByCreatedAtDescIdDesc(order.getId(),PaymentStatus.PAID)
                .filter(p->p.getPaidAt()!=null && p.getProviderPaymentId()!=null).isPresent(),"Aguarde pagamento confirmado pelo backend.");
    }
    private static void require(boolean condition,String message) { if(!condition) throw new ResourceConflictException(message); }
}
