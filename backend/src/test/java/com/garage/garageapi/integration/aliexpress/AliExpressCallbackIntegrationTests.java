package com.garage.garageapi.integration.aliexpress;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.BeforeEach;

import static org.mockito.Mockito.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AliExpressCallbackIntegrationTests {
    private static final String CALLBACK = "/api/integrations/aliexpress/callback";

    @Autowired MockMvc mockMvc;
    @MockitoBean AliExpressAuthService authService;

    @BeforeEach
    void resetAuthService() {
        reset(authService);
    }

    @Test
    void anonymousCallbackWithoutParametersIsAccepted() throws Exception {
        mockMvc.perform(get(CALLBACK))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Callback AliExpress incompleto: codigo de autorizacao ausente."));
        verifyNoInteractions(authService);
    }

    @Test
    void parametersAreAcceptedWithoutLeakingTheirValues(CapturedOutput output) throws Exception {
        mockMvc.perform(get(CALLBACK)
                        .param("code", "private-authorization-code")
                        .param("state", "private-oauth-state")
                        .param("error", "provider-error-value")
                        .param("error_description", "private-provider-description"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Autorizacao AliExpress recusada ou interrompida. Tente novamente."));
        verifyNoInteractions(authService);
        assertThat(output.getOut()).contains("AliExpress callback received", "code=present",
                        "state=present", "error=present", "error_description=present")
                .doesNotContain("private-authorization-code", "private-oauth-state",
                        "provider-error-value", "private-provider-description");
    }

    @Test
    void publicAccessIsLimitedToTheExactGetRoute() throws Exception {
        mockMvc.perform(post(CALLBACK)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/integrations/aliexpress/private"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/orders")).andExpect(status().isUnauthorized());
        verifyNoInteractions(authService);
    }

    @Test
    void validCodeCompletesAuthorizationWithoutReturningTokens() throws Exception {
        when(authService.exchangeCode("test-code"))
                .thenReturn(new AliExpressTokenData("synthetic-access", "synthetic-refresh", 3600L,
                        7200L, null, null, "synthetic-user", null));
        mockMvc.perform(get(CALLBACK).param("code", "test-code").param("state", "test-state"))
                .andExpect(status().isOk())
                .andExpect(content().string("Autorizacao AliExpress concluida com sucesso."));
        verify(authService).exchangeCode("test-code");
    }

    @Test
    void exchangeFailureHasSafeResponse() throws Exception {
        when(authService.exchangeCode("test-code")).thenThrow(new AliExpressAuthException());
        mockMvc.perform(get(CALLBACK).param("code", "test-code"))
                .andExpect(status().isBadGateway())
                .andExpect(content().string("Nao foi possivel concluir a autorizacao AliExpress. Tente autorizar novamente."));
        verify(authService).exchangeCode("test-code");
    }

    @Test
    void blankCodeIsRejectedWithoutExchange() throws Exception {
        mockMvc.perform(get(CALLBACK).param("code", " "))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(authService);
    }
}
