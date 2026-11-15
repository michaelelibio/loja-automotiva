package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.payment.entity.PaymentStatus;
import com.garage.garageapi.payment.gateway.CheckoutProGateway;
import com.garage.garageapi.shared.exception.ResourceConflictException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Component
public class AliExpressDispatchValidation {
    private final AliExpressProductClient products;
    private final AliExpressTokenService tokens;
    private final CheckoutProGateway mercadoPago;
    private final String appHash;
    public AliExpressDispatchValidation(AliExpressProductClient products, AliExpressProductNormalizer normalizer,
            AliExpressTokenService tokens, CheckoutProGateway mercadoPago, @Value("${aliexpress.app-key:}") String appKey) {
        this.products=products;this.tokens=tokens;this.mercadoPago=mercadoPago;
        this.appHash=AliExpressOAuthStateService.hash(appKey);
    }
    public String buyerHash() {
        return AliExpressOAuthStateService.hash(tokens.buyerIdentity().userId());
    }
    public String appHash() { return appHash; }
    public String blockedReason(Order order, AliExpressDispatchQuote quote) {
        // Financial quotations are optional observations, never dispatch gates.
        return null;
    }
    public AliExpressSupplierCosts verifyProducts(AliExpressDispatchQuote quote, String access) {
        return verifyProducts(quote,access,buyerHash());
    }
    public AliExpressSupplierCosts verifyProducts(AliExpressDispatchQuote quote, String access,String accessBuyerHash) {
        if (!Objects.equals(accessBuyerHash,quote.buyerHash())) throw new ResourceConflictException("Conta compradora AliExpress alterada. Revalide a ausência de duplicidade.");
        Map<String,tools.jackson.databind.JsonNode> catalog=new HashMap<>();
        Map<String,Integer> quantities=new HashMap<>();
        for(var line:quote.lines()) quantities.merge(line.productId()+":"+line.skuId(),line.quantity(),Integer::sum);
        BigDecimal productTotal=BigDecimal.ZERO;boolean allPrices=true;String currency=null;
        Instant deadline=Instant.now().plusSeconds(30);
        for (var line:quote.lines()) {
            if (!Instant.now().isBefore(deadline)) throw new ResourceConflictException("Validação do fornecedor excedeu o prazo. Nenhum pedido foi enviado.");
            var product=catalog.computeIfAbsent(line.productId(),id->{
                var raw=products.get(id,"BR","BRL","pt",access);
                if(raw==null || !"onSelling".equalsIgnoreCase(raw.path("ae_item_base_info_dto").path("product_status_type").asText()))
                    throw new ResourceConflictException("Produto sem disponibilidade de venda confirmada no AliExpress.");
                return raw;
            });
            var skus=product.path("ae_item_sku_info_dtos");
            if(!skus.isArray())skus=skus.path("ae_item_sku_info_d_t_o");
            tools.jackson.databind.JsonNode sku=null;
            if(skus.isArray())for(var candidate:skus)if(line.skuId().equals(candidate.path("sku_id").asText())){sku=candidate;break;}
            if(sku==null)throw new ResourceConflictException("Variante indisponível no fornecedor.");
            int needed=quantities.get(line.productId()+":"+line.skuId());
            if (!Objects.equals(line.skuAttr(),sku.path("sku_attr").asText()) || !sku.path("sku_available_stock").asText().matches("[0-9]{1,9}")
                    || Integer.parseInt(sku.path("sku_available_stock").asText())<needed)
                throw new ResourceConflictException("Variante divergente ou estoque insuficiente no AliExpress.");
            String money=sku.path("offer_sale_price").asText();
            if(!money.matches("[0-9]{1,10}(\\.[0-9]{1,4})?"))money=sku.path("sku_price").asText();
            String unitCurrency=sku.path("currency_code").asText();
            if(!money.matches("[0-9]{1,10}(\\.[0-9]{1,4})?") || !unitCurrency.matches("[A-Z]{3}") || currency!=null && !currency.equals(unitCurrency))allPrices=false;
            else {currency=unitCurrency;productTotal=productTotal.add(new BigDecimal(money).multiply(BigDecimal.valueOf(line.quantity())));}
        }
        return new AliExpressSupplierCosts("LIVE_PRODUCT_API",allPrices?currency:null,allPrices?productTotal:null,null,null,null,Instant.now());
    }
    public void verifyPayment(Long orderId, Long paymentId, String providerId, BigDecimal total) {
        var result=mercadoPago.findPayment(providerId);
        if (result==null || result.status()!=PaymentStatus.PAID || !Boolean.TRUE.equals(result.liveMode())
                || result.approvedAt()==null || result.refundedAmount()==null || result.refundedAmount().signum()!=0
                || !Objects.equals(providerId,result.providerPaymentId())
                || !Objects.equals("garage_order_"+orderId+"_payment_"+paymentId,result.externalReference())
                || !"BRL".equals(result.currencyId()) || result.transactionAmount()==null || total.compareTo(result.transactionAmount())!=0)
            throw new ResourceConflictException("Pagamento atual não confirmado em produção pelo Mercado Pago. Encaminhamento bloqueado.");
    }
}
