package com.garage.garageapi.integration.aliexpress;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.garage.garageapi.integration.aliexpress.AliExpressFreightDiagnosticResult.Status.*;

@SpringBootTest
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class AliExpressTerritorialDiagnosticIntegrationTests {
    @Autowired MockMvc mvc;
    @MockitoBean AliExpressTokenService tokens;
    @MockitoBean AliExpressTerritorialClient client;
    @MockitoBean AliExpressDsFreightClient freight;
    @MockitoBean AliExpressOrderClient orders;
    static final String PATH="/api/admin/integrations/aliexpress/territorial-diagnostic";
    @BeforeEach void resetMocks() { reset(tokens,client,freight,orders); }
    @AfterEach void noCommercialOperations() { verifyNoInteractions(freight,orders); }
    @Test void adminOnlyPostAndNoGetSideEffects() throws Exception {
        mvc.perform(post(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))).andExpect(status().isForbidden());
        mvc.perform(get(PATH).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(tokens,client);
    }
    @Test void usesPersistedTokenOnceAndNeverRefreshesOrExposesIt() throws Exception {
        when(tokens.existingAccessToken()).thenReturn("synthetic-token");
        when(client.query("synthetic-token")).thenReturn(AliExpressTerritorialDiagnosticResult.of(SUCCESS,"TERRITORIAL_QUERY_SUCCEEDED",true,"BR",List.of(),List.of("children")));
        mvc.perform(post(PATH).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.country").value("BR")).andExpect(jsonPath("$.applicationAccess").value("CALL_ACCEPTED"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("synthetic-token"))));
        verify(tokens).existingAccessToken();verify(client).query("synthetic-token");verifyNoMoreInteractions(tokens,client);
    }
    @Test void unreadableTokenReturnsSafeLocalFailureWithoutProviderCall() throws Exception {
        when(tokens.existingAccessToken()).thenThrow(new IllegalStateException("synthetic-secret"));
        mvc.perform(post(PATH).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.safeCode").value("LOCAL_TOKEN_UNAVAILABLE"))
                .andExpect(jsonPath("$.applicationAccess").value("NOT_CONFIRMED"));
        verifyNoInteractions(client);verify(tokens,never()).accessToken();
    }
    @Test void manualTokenInBodyOrQueryCannotOverrideTheStoredBackendToken() throws Exception {
        when(tokens.existingAccessToken()).thenReturn("synthetic-stored-token");
        when(client.query("synthetic-stored-token")).thenReturn(AliExpressTerritorialDiagnosticResult.failure(TOKEN_REJECTED,"PROVIDER_TOKEN_REJECTED"));
        mvc.perform(post(PATH).queryParam("access_token","synthetic-manual-token")
                .contentType("application/json").content("{\"accessToken\":\"synthetic-manual-token\",\"countryCode\":\"US\"}")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.safeCode").value("PROVIDER_TOKEN_REJECTED"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("synthetic-manual-token"))));
        verify(tokens).existingAccessToken();verify(client).query("synthetic-stored-token");verifyNoMoreInteractions(tokens,client);
    }
}
