package com.garage.garageapi.integration.aliexpress;

import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class AliExpressFreightDiagnosticServiceTests {
    AliExpressTokenService tokens=mock(AliExpressTokenService.class);
    AliExpressFreightDiagnosticClient freight=mock(AliExpressFreightDiagnosticClient.class);
    AliExpressProductClient products=mock(AliExpressProductClient.class);
    AliExpressProductNormalizer normalizer=mock(AliExpressProductNormalizer.class);
    AliExpressFreightDiagnosticService service=new AliExpressFreightDiagnosticService(tokens,freight,products,normalizer);
    static final String ID="1005010661577657", SKU="12000053109276547";
    static final AliExpressFreightDiagnosticRequest REQUEST=new AliExpressFreightDiagnosticRequest(ID,SKU,1);

    @BeforeEach void setup() { when(tokens.existingAccessToken()).thenReturn("synthetic-token"); }
    void product(Map<String,String> attributes, int stock) {
        var variant=new AliExpressProductPreview.Variant(SKU,null,null,attributes,List.of(),BigDecimal.ONE,BigDecimal.ONE,"USD",stock);
        when(normalizer.normalize(any(),eq(ID),eq("BR"))).thenReturn(new AliExpressProductPreview(ID,"Test","","ALIEXPRESS","USD",
                List.of(),List.of(variant),Map.of(),Map.of(),Map.of("ship_to_country","BR"),
                Map.of("store_country_code","CN"),"BR","",false,List.of(),""));
    }
    @Test void noOriginAttributeIsNeededByDsFreightQuery() {
        product(Map.of("14:name","Color","14","Black"),10);
        var input=new AliExpressFreightDiagnosticClient.Input(ID,SKU,1,"BR");
        when(freight.query("synthetic-token",input)).thenReturn(AliExpressFreightDiagnosticResult.failure(AliExpressFreightDiagnosticResult.Status.PRODUCT_UNAVAILABLE,"NO_DELIVERY_METHODS"));
        assertThat(service.run(REQUEST).safeCode()).isEqualTo("NO_DELIVERY_METHODS");
        verify(freight).query("synthetic-token",input);verify(tokens,never()).accessToken();
    }
    @Test void revalidatesSourceThenCallsFreightOnceWithPersistedTokenAndExactSku() {
        product(Map.of("x:name","Ships From","x","Brazil"),10);
        var input=new AliExpressFreightDiagnosticClient.Input(ID,SKU,1,"BR");
        var response=AliExpressFreightDiagnosticResult.failure(AliExpressFreightDiagnosticResult.Status.PERMISSION_DENIED,"PROVIDER_PERMISSION_DENIED");
        when(freight.query("synthetic-token",input)).thenReturn(response);
        assertThat(service.run(REQUEST)).isSameAs(response);
        var order=inOrder(tokens,products,normalizer,freight);
        order.verify(tokens).existingAccessToken();order.verify(products).get(ID,"BR","BRL","en","synthetic-token");
        order.verify(normalizer).normalize(any(),eq(ID),eq("BR"));order.verify(freight).query("synthetic-token",input);
        verifyNoMoreInteractions(tokens,products,normalizer,freight);
    }
    @Test void unknownSkuAndInsufficientStockDoNotCallFreight() {
        product(Map.of("x:name","Ships From","x","BR"),0);
        assertThat(service.run(REQUEST).safeCode()).isEqualTo("INSUFFICIENT_SUPPLIER_STOCK");
        assertThat(service.run(new AliExpressFreightDiagnosticRequest(ID,"999",1)).safeCode()).isEqualTo("SKU_NOT_IN_PRODUCT");
        verifyNoInteractions(freight);
    }
    @Test void invalidQuantityNeverReadsAuthorizationOrCallsSupplier() {
        assertThat(service.run(new AliExpressFreightDiagnosticRequest(ID,SKU,0)).safeCode()).isEqualTo("INVALID_TEST_INPUT");
        assertThat(service.run(new AliExpressFreightDiagnosticRequest(ID,SKU,101)).safeCode()).isEqualTo("INVALID_TEST_INPUT");
        verifyNoInteractions(tokens,products,freight);
    }
    @Test void productPermissionFailureIsNotEvidenceAboutFreightPermission() {
        when(products.get(anyString(),anyString(),anyString(),anyString(),anyString()))
                .thenThrow(new AliExpressIntegrationException(AliExpressIntegrationException.Reason.PERMISSION));
        var result=service.run(REQUEST);
        assertThat(result.status()).isEqualTo(AliExpressFreightDiagnosticResult.Status.PERMISSION_DENIED);
        assertThat(result.safeCode()).isEqualTo("PRODUCT_LOOKUP_FAILED");verifyNoInteractions(freight);
    }
    @Test void prepareReturnsOnlyAllowlistedProductDataWithoutRenewingToken() {
        product(Map.of("x:name","Ships From","x","China"),10);
        var prepared=service.prepare(ID);
        assertThat(prepared.variants()).containsExactly(new AliExpressFreightDiagnosticService.Option(SKU,10));
        assertThat(prepared.toString()).doesNotContain("synthetic-token","store_country_code");
        verify(tokens).existingAccessToken();verify(tokens,never()).accessToken();verifyNoInteractions(freight);
    }
}
