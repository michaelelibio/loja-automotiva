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
    static final String PATH="/api/admin/integrations/aliexpress/freight-diagnostic";
    @BeforeEach void resetMocks() { reset(tokens,client); }
    @Test void onlyAdminCanTriggerPostAndGetCannotPerformQuery() throws Exception {
        mvc.perform(post(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))).andExpect(status().isForbidden());
        mvc.perform(get(PATH).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(tokens,client);
    }
    @Test void adminUsesExistingTokenExactlyOnceWithoutRefresh() throws Exception {
        when(tokens.existingAccessToken()).thenReturn("synthetic-token");
        when(client.query("synthetic-token")).thenReturn(AliExpressFreightDiagnosticResult.failure(
                AliExpressFreightDiagnosticResult.Status.PARAMETER_ERROR,"PROVIDER_PARAMETER_REJECTED"));
        mvc.perform(post(PATH).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.status").value("PARAMETER_ERROR"))
                .andExpect(jsonPath("$.methods.length()").value(0)).andExpect(jsonPath("$.accessToken").doesNotExist());
        verify(tokens).existingAccessToken();verify(tokens,never()).accessToken();verify(client).query("synthetic-token");
        verifyNoMoreInteractions(tokens,client);
    }
    @Test void expiredOrUnreadableLocalTokenNeverCallsProvider() throws Exception {
        when(tokens.existingAccessToken()).thenThrow(new IllegalStateException("synthetic-secret"));
        mvc.perform(post(PATH).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("LOCAL_AUTHORIZATION_UNAVAILABLE"));
        verifyNoInteractions(client);verify(tokens,never()).accessToken();
    }
    @Test void exactProtocolCodeReachesAdminWithoutRefreshingCredentials() throws Exception {
        when(tokens.existingAccessToken()).thenReturn("synthetic-token");
        when(client.query("synthetic-token")).thenReturn(AliExpressFreightDiagnosticResult.failure(
                AliExpressFreightDiagnosticResult.Status.PROTOCOL_ERROR,"TOP_25_INVALID_SIGNATURE"));
        mvc.perform(post(PATH).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROTOCOL_ERROR"))
                .andExpect(jsonPath("$.safeCode").value("TOP_25_INVALID_SIGNATURE"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        verify(tokens).existingAccessToken();verify(tokens,never()).accessToken();
        verify(client).query("synthetic-token");verifyNoMoreInteractions(tokens,client);
    }
}
