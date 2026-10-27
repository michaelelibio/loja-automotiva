package com.garage.garageapi.integration.aliexpress;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class AliExpressFreightDiagnosticIntegrationTests {
    @Autowired MockMvc mvc;
    @MockitoBean AliExpressTokenService tokens;
    @MockitoBean AliExpressFreightDiagnosticClient client;
    @MockitoBean AliExpressProductClient products;
    @MockitoBean AliExpressProductNormalizer normalizer;
    static final String BODY="{\"productId\":\"123\",\"skuId\":\"456\",\"quantity\":1}";
    static final AliExpressFreightDiagnosticClient.Input INPUT=new AliExpressFreightDiagnosticClient.Input("123","456",1,"BR");
    static final String PATH="/api/admin/integrations/aliexpress/freight-diagnostic";
    @BeforeEach void resetMocks() { reset(tokens,client,products,normalizer);
        var variant=new AliExpressProductPreview.Variant("456",null,null,java.util.Map.of("x:name","Ships From","x","Brazil"),java.util.List.of(),java.math.BigDecimal.ONE,java.math.BigDecimal.ONE,"BRL",10);
        when(normalizer.normalize(any(),eq("123"),eq("BR"))).thenReturn(new AliExpressProductPreview("123","Test","","ALIEXPRESS","BRL",java.util.List.of(),java.util.List.of(variant),java.util.Map.of(),java.util.Map.of(),java.util.Map.of(),java.util.Map.of(),"BR","",false,java.util.List.of(),"")); }
    @Test void onlyAdminCanTriggerPostAndGetCannotPerformQuery() throws Exception {
        mvc.perform(post(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))).andExpect(status().isForbidden());
        mvc.perform(get(PATH).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(tokens,client);
    }
    @Test void provinceComparisonRequiresAdminAndUsesOnlyPersistedToken() throws Exception {
        String path=PATH+"/province-comparison";
        mvc.perform(post(path).contentType("application/json").content(BODY)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))).andExpect(status().isForbidden());
        mvc.perform(get(path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(tokens,client,products);
        mvc.perform(post(path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.executionStatus").value("NOT_EXECUTED"));
        when(tokens.existingAccessToken()).thenReturn("synthetic-token");
        var diagnostic=AliExpressFreightDiagnosticResult.failure(AliExpressFreightDiagnosticResult.Status.INVALID_RESPONSE,"AMBIGUOUS_MONETARY_UNIT");
        var observed=new AliExpressDsFreightClient.Observation(new AliExpressDsFreightClient.RequestEvidence(java.util.List.of("shipToCountry"),"BR",null,true,true),diagnostic,
                java.util.List.of(new AliExpressDsFreightClient.DeliveryEvidence(0,"STANDARD","CN","CROSS_BORDER_ROUTE_OBSERVED")));
        when(client.queryObserved(eq("synthetic-token"),any())).thenReturn(observed);
        mvc.perform(post(path).contentType("application/json").content(BODY).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.executionStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.withProvince.result.safeCode").value("AMBIGUOUS_MONETARY_UNIT"))
                .andExpect(jsonPath("$.countryOnly.deliveryOptions[0].shipFromCountry").value("CN"))
                .andExpect(jsonPath("$.withProvince.deliveryOptions[0].serviceCode").value("STANDARD"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("synthetic-token"))));
        verify(tokens).existingAccessToken();verify(tokens,never()).accessToken();
        verify(client).queryObserved("synthetic-token",INPUT);
        verify(client).queryObserved("synthetic-token",new AliExpressFreightDiagnosticClient.Input("123","456",1,"BR","Santa Catarina"));
        verifyNoMoreInteractions(client,tokens);
    }
    @Test void adminUsesExistingTokenExactlyOnceWithoutRefresh() throws Exception {
        when(tokens.existingAccessToken()).thenReturn("synthetic-token");
        when(client.query("synthetic-token",INPUT)).thenReturn(AliExpressFreightDiagnosticResult.failure(
                AliExpressFreightDiagnosticResult.Status.PARAMETER_ERROR,"PROVIDER_PARAMETER_REJECTED"));
        mvc.perform(post(PATH).contentType("application/json").content(BODY).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.status").value("PARAMETER_ERROR"))
                .andExpect(jsonPath("$.methods.length()").value(0)).andExpect(jsonPath("$.accessToken").doesNotExist());
        verify(tokens).existingAccessToken();verify(tokens,never()).accessToken();verify(client).query("synthetic-token",INPUT);
        verifyNoMoreInteractions(tokens,client);
    }
    @Test void expiredOrUnreadableLocalTokenNeverCallsProvider() throws Exception {
        when(tokens.existingAccessToken()).thenThrow(new IllegalStateException("synthetic-secret"));
        mvc.perform(post(PATH).contentType("application/json").content(BODY).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("LOCAL_AUTHORIZATION_UNAVAILABLE"));
        verifyNoInteractions(client);verify(tokens,never()).accessToken();
    }
    @Test void missingInputNeverCallsProviderOrToken() throws Exception {
        mvc.perform(post(PATH).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isOk())
            .andExpect(jsonPath("$.safeCode").value("INVALID_TEST_INPUT"));
        verifyNoInteractions(tokens,client,products);
    }
    @Test void amountIsSerializedAsExactDecimalStringAndOnlySafeDetailsAreReturned() throws Exception {
        when(tokens.existingAccessToken()).thenReturn("synthetic-token");
        var method=new AliExpressFreightDiagnosticResult.Method("STANDARD",new java.math.BigDecimal("99999999999999.99"),"BRL","5-8days","AliExpress Standard","BR");
        when(client.query("synthetic-token",INPUT)).thenReturn(AliExpressFreightDiagnosticResult.result(
                AliExpressFreightDiagnosticResult.Status.SUCCESS,"CALL_SUCCEEDED",java.util.List.of(method)));
        mvc.perform(post(PATH).contentType("application/json").content(BODY).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.methods[0].amount").isString())
                .andExpect(jsonPath("$.methods[0].amount").value("99999999999999.99"))
                .andExpect(jsonPath("$.methods[0].company").value("AliExpress Standard"))
                .andExpect(jsonPath("$.methods[0].originCountry").value("BR"))
                .andExpect(jsonPath("$.limitations").value(org.hamcrest.Matchers.hasItem("COUNTRY_ONLY_BR_DIAGNOSTIC")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("synthetic-token"))));
        verify(tokens).existingAccessToken();verify(tokens,never()).accessToken();verify(client).query("synthetic-token",INPUT);
    }
    @Test void productPreparationAlsoRequiresAdminAndCannotRunViaGet() throws Exception {
        mvc.perform(post(PATH+"/product").contentType("application/json").content("{\"productId\":\"1005010661577657\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(PATH+"/product").contentType("application/json").content("{\"productId\":\"1005010661577657\"}")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))).andExpect(status().isForbidden());
        mvc.perform(get(PATH+"/product").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(tokens,client,products);
    }
    @Test void preparationWithUnavailableTokenReturnsSafeNoStoreError() throws Exception {
        when(tokens.existingAccessToken()).thenThrow(new IllegalStateException("synthetic-secret"));
        mvc.perform(post(PATH+"/product").contentType("application/json").content("{\"productId\":\"1005010661577657\"}")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isConflict()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("synthetic-secret"))));
        verifyNoInteractions(client,products);verify(tokens,never()).accessToken();
    }
    @Test void exactProtocolCodeReachesAdminWithoutRefreshingCredentials() throws Exception {
        when(tokens.existingAccessToken()).thenReturn("synthetic-token");
        when(client.query("synthetic-token",INPUT)).thenReturn(AliExpressFreightDiagnosticResult.failure(
                AliExpressFreightDiagnosticResult.Status.PROTOCOL_ERROR,"TOP_25_INVALID_SIGNATURE"));
        mvc.perform(post(PATH).contentType("application/json").content(BODY).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROTOCOL_ERROR"))
                .andExpect(jsonPath("$.safeCode").value("TOP_25_INVALID_SIGNATURE"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        verify(tokens).existingAccessToken();verify(tokens,never()).accessToken();
        verify(client).query("synthetic-token",INPUT);verifyNoMoreInteractions(tokens,client);
    }
}
