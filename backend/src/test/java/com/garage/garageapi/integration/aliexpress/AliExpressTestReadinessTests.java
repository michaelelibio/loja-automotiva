package com.garage.garageapi.integration.aliexpress;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class AliExpressTestReadinessTests {
    AliExpressProductClient products=mock(AliExpressProductClient.class);
    AliExpressTokenService tokens=mock(AliExpressTokenService.class);
    AliExpressTestFixture fixture=new AliExpressTestFixture("ingarage-test-19-v1","owner-authorized-19","1005008752784588","201","14:771#Black",1,null,"BRL",null,null,null,null,
            Map.ofEntries(Map.entry("country","BR"),Map.entry("province","Santa Catarina"),Map.entry("city","Joinville"),Map.entry("address","Test Street, 19"),Map.entry("address2","Centro"),Map.entry("zip","89229040"),Map.entry("contact_person","Test Owner"),Map.entry("full_name","Test Owner"),Map.entry("cpf","52998224725"),Map.entry("mobile_no","47999999999"),Map.entry("phone_country","+55"),Map.entry("locale","pt_BR")),LocalDate.of(1990,1,1));
    AliExpressTestReadiness readiness(){return new AliExpressTestReadiness(products,new AliExpressProductNormalizer(),tokens);}
    @Test void absentBuyerMetadataAndFailedProductReadAreWarningsNotArtificialBlocks(){
        when(tokens.buyerIdentity()).thenThrow(new IllegalStateException("private-token"));when(products.get(anyString(),anyString(),anyString(),anyString(),anyString())).thenThrow(new AliExpressIntegrationException(AliExpressIntegrationException.Reason.PERMISSION));
        var review=readiness().check(fixture,"synthetic-token");assertThat(review.buyerState()).isEqualTo("UNKNOWN");assertThat(review.fixture().productCost()).isNull();assertThat(review.fixture().shippingCost()).isNull();assertThat(review.fixture().taxCost()).isNull();assertThat(review.fixture().totalCost()).isNull();assertThat(review.warnings().toString()).doesNotContain("private-token");assertThat(review.fixture().valid()).isTrue();verify(tokens,never()).accessToken();
    }
    JsonNode product()throws Exception{var raw=(tools.jackson.databind.node.ObjectNode)JsonMapper.builder().build().readTree(getClass().getResourceAsStream("/aliexpress-product.json"));((tools.jackson.databind.node.ObjectNode)raw.path("logistics_info_dto")).put("ship_to_country","BR");for(var sku:raw.path("ae_item_sku_info_dtos").path("ae_item_sku_info_d_t_o"))((tools.jackson.databind.node.ObjectNode)sku).put("currency_code","BRL");return raw;}
    @Test void higherLiveProductCostDoesNotCreateBudgetCeilingOrInventOtherCosts()throws Exception{
        when(products.get(anyString(),anyString(),anyString(),anyString(),anyString())).thenReturn(product());var review=readiness().check(fixture.withProductCost(BigDecimal.ONE),"token");assertThat(review.fixture().productCost()).isEqualByComparingTo("8.90");assertThat(review.fixture().totalCost()).isNull();assertThat(review.productCostSource()).isEqualTo("LIVE_PRODUCT_READ");assertThat(review.fixture().payloadDigest()).isEqualTo(fixture.payloadDigest());assertThat(review.fixture().digest()).isNotEqualTo(fixture.digest());
    }
    @Test void anImportedPriceRemainsLabeledAsImportedWhenReadFails(){when(products.get(anyString(),anyString(),anyString(),anyString(),anyString())).thenThrow(new RuntimeException());var result=readiness().check(fixture.withProductCost(new BigDecimal("100")),"token");assertThat(result.fixture().productCost()).isEqualByComparingTo("100");assertThat(result.productCostSource()).isEqualTo("IMPORTED_VARIANT");}
    @Test void unknownLogisticsIsOmittedFromDocumentedPayloadRatherThanInvented(){assertThat(fixture.valid()).isTrue();assertThat(fixture.payload().toString()).doesNotContain("logistics_service_name","CATALOG_FREE","shipping_price","payment");assertThat(fixture.payload()).containsEntry("out_order_id","ingarage-test-19-v1");}
    @Test void currentBuyerIdentityIsShownWhenItWasActuallyReturnedByOAuth(){when(tokens.buyerIdentity()).thenReturn(new AliExpressTokenService.BuyerIdentity("buyer-19","account-19"));var result=readiness().check(fixture,"token");assertThat(result.buyerState()).isEqualTo("KNOWN");assertThat(result.buyerUserId()).isEqualTo("buyer-19");assertThat(result.buyerAccount()).contains("account-19");}
}
