package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.payment.entity.PaymentStatus;
import com.garage.garageapi.payment.gateway.CheckoutProGateway;
import com.garage.garageapi.shared.exception.ResourceConflictException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AliExpressDispatchValidationTests {
    final AliExpressProductClient products=mock(AliExpressProductClient.class);
    final AliExpressProductNormalizer normalizer=mock(AliExpressProductNormalizer.class);
    final AliExpressTokenService tokens=mock(AliExpressTokenService.class);
    final CheckoutProGateway mercadoPago=mock(CheckoutProGateway.class);
    final AliExpressDispatchValidation validation=new AliExpressDispatchValidation(products,normalizer,tokens,mercadoPago,"synthetic-app");
    @BeforeEach void setup() {
        when(tokens.buyerIdentity()).thenReturn(new AliExpressTokenService.BuyerIdentity("synthetic-buyer",null));
        when(products.get(anyString(),anyString(),anyString(),anyString(),anyString())).thenReturn(
                tools.jackson.databind.json.JsonMapper.builder().build().readTree("{\"ae_item_base_info_dto\":{\"product_status_type\":\"onSelling\"}}"));
    }
    @ParameterizedTest @ValueSource(strings={"pending","refunded","unknownRefund","sandbox","unknownMode","amount","currency","reference","id","approvedAt"})
    void invalidOrRefundedPaymentNeverPassesDispatch(String kind) {
        var result=new CheckoutProGateway.PaymentResult("id".equals(kind)?"wrong":"remote", "reference".equals(kind)?"wrong":"garage_order_21_payment_1",
                "pending".equals(kind)?PaymentStatus.PENDING:PaymentStatus.PAID,"approved","amount".equals(kind)?BigDecimal.ONE:BigDecimal.TEN,
                "currency".equals(kind)?"USD":"BRL","pix","pix","approvedAt".equals(kind)?null:Instant.now(),
                "unknownMode".equals(kind)?null:!"sandbox".equals(kind),"unknownRefund".equals(kind)?null:"refunded".equals(kind)?BigDecimal.ONE:BigDecimal.ZERO);
        when(mercadoPago.findPayment("remote")).thenReturn(result);
        assertThatThrownBy(()->validation.verifyPayment(21L,1L,"remote",BigDecimal.TEN)).isInstanceOf(ResourceConflictException.class);
        verifyNoInteractions(products,normalizer,tokens);
    }
    @Test void authenticatedLiveApprovedPaymentWithExactFinancialAssociationPasses() {
        when(mercadoPago.findPayment("remote")).thenReturn(new CheckoutProGateway.PaymentResult("remote","garage_order_21_payment_1",
                PaymentStatus.PAID,"approved",BigDecimal.TEN,"BRL","pix","pix",Instant.now(),true,BigDecimal.ZERO));
        validation.verifyPayment(21L,1L,"remote",BigDecimal.TEN);
        verify(mercadoPago).findPayment("remote");
    }
    @ParameterizedTest @ValueSource(strings={"stock","sku","buyer","offSale"})
    void changedSupplierOfferAccountOrAvailabilityBlocksCreation(String kind) {
        if("buyer".equals(kind))when(tokens.buyerIdentity()).thenReturn(new AliExpressTokenService.BuyerIdentity("other-buyer",null));
        if("offSale".equals(kind))when(products.get(anyString(),anyString(),anyString(),anyString(),anyString())).thenReturn(
                tools.jackson.databind.json.JsonMapper.builder().build().readTree("{\"ae_item_base_info_dto\":{\"product_status_type\":\"offSelling\"}}"));
        var sku=new AliExpressProductPreview.Variant("123","code","sku".equals(kind)?"14:2":"14:1",Map.of(),List.of(),
                "price".equals(kind)?new BigDecimal("10.51"):new BigDecimal("10.50"),null,"currency".equals(kind)?"USD":"BRL","stock".equals(kind)?0:5);
        when(normalizer.normalize(any(),anyString(),anyString())).thenReturn(new AliExpressProductPreview("1005007170269840","Item","Description","ALIEXPRESS","BRL",List.of(),List.of(sku),Map.of(),Map.of(),Map.of(),Map.of(),"BR",null,false,List.of(),"fingerprint"));
        if(!"offSale".equals(kind))when(products.get(anyString(),anyString(),anyString(),anyString(),anyString())).thenReturn(
            tools.jackson.databind.json.JsonMapper.builder().build().readTree("{\"ae_item_base_info_dto\":{\"product_status_type\":\"onSelling\"},\"ae_item_sku_info_dtos\":[{\"sku_id\":\"123\",\"sku_attr\":\""+("sku".equals(kind)?"14:2":"14:1")+"\",\"sku_available_stock\":"+("stock".equals(kind)?0:5)+"}]}"));
        assertThatThrownBy(()->validation.verifyProducts(quote(),"synthetic-token")).isInstanceOf(ResourceConflictException.class);
    }
    @ParameterizedTest @ValueSource(strings={"999.00","unknown","USD"})
    void excessiveOrUnknownMoneyDoesNotBlockValidTechnicalData(String kind) {
        when(products.get(anyString(),anyString(),anyString(),anyString(),anyString())).thenReturn(
            tools.jackson.databind.json.JsonMapper.builder().build().readTree("{\"ae_item_base_info_dto\":{\"product_status_type\":\"onSelling\"},\"ae_item_sku_info_dtos\":[{\"sku_id\":\"123\",\"sku_attr\":\"14:1\",\"sku_available_stock\":5,\"offer_sale_price\":\""+("unknown".equals(kind)?"unknown":"999.00")+"\",\"currency_code\":\""+("USD".equals(kind)?"USD":"BRL")+"\"}]}"));
        var costs=validation.verifyProducts(quote(),"synthetic-token");
        assertThat(costs.total()).isNull();assertThat(costs.taxTotal()).isNull();
        if("unknown".equals(kind))assertThat(costs.productTotal()).isNull();else assertThat(costs.productTotal()).isEqualByComparingTo("999.00");
    }
    private AliExpressDispatchQuote quote() {
        return new AliExpressDispatchQuote("context",AliExpressOAuthStateService.hash("synthetic-buyer"),validation.appHash(),
                List.of(new AliExpressDispatchQuote.Line("1005007170269840","123","14:1",1,new BigDecimal("10.50"),"SERVICE")),
                new BigDecimal("10.50"),BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("10.50"),Instant.now(),Instant.now().plusSeconds(600),"admin","supplier-quote");
    }
    @ParameterizedTest @ValueSource(strings={"absent","null","empty","multiple","properties","stock"})
    void emptySelectionRequiresOneLivePropertylessSkuAndSufficientStock(String kind) {
        var mapper=tools.jackson.databind.json.JsonMapper.builder().build();
        var raw=mapper.readTree("{\"ae_item_base_info_dto\":{\"product_status_type\":\"onSelling\"},\"ae_item_sku_info_dtos\":[{\"sku_id\":\"123\",\"sku_available_stock\":5}]}");
        var list=(tools.jackson.databind.node.ArrayNode)raw.path("ae_item_sku_info_dtos");
        var sku=(tools.jackson.databind.node.ObjectNode)list.get(0);
        if("null".equals(kind))sku.putNull("sku_attr");
        if("empty".equals(kind))sku.put("sku_attr","");
        if("multiple".equals(kind))list.add(mapper.readTree("{\"sku_id\":\"456\"}"));
        if("properties".equals(kind))sku.set("ae_sku_property_dtos",mapper.readTree("[{\"sku_property_id\":14}]"));
        if("stock".equals(kind))sku.put("sku_available_stock",0);
        when(products.get(anyString(),anyString(),anyString(),anyString(),anyString())).thenReturn(raw);
        var q=quote();
        var single=new AliExpressDispatchQuote(q.contextDigest(),q.buyerHash(),q.appHash(),
                List.of(new AliExpressDispatchQuote.Line("1005007170269840","123","",1,null,null)),
                null,null,null,null,Instant.now(),null,null,null);
        if(Set.of("multiple","properties","stock").contains(kind))
            assertThatThrownBy(()->validation.verifyProducts(single,"synthetic-token")).isInstanceOf(ResourceConflictException.class);
        else assertThatCode(()->validation.verifyProducts(single,"synthetic-token")).doesNotThrowAnyException();
    }
}
