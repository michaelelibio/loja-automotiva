package com.garage.garageapi.auth.email;

import com.garage.garageapi.auth.exception.AccountEmailDeliveryException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

class ResendAccountEmailServiceTests {
    private static final String TEST_KEY = "test-only-resend-key";

    @Test
    void sendsVerificationAndResetUsingOfficialResendContractWithoutRealCalls() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.resend.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ResendAccountEmailService service = new ResendAccountEmailService(
                builder.build(), TEST_KEY, "GARAGE <onboarding@resend.dev>");

        server.expect(once(), requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_KEY))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"from":"GARAGE <onboarding@resend.dev>",
                         "to":["cliente@example.com"],
                         "subject":"Confirme seu e-mail — GARAGE"}
                        """, false))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Confirmar e-mail")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("https://garage.example/verify-email?token=opaque")))
                .andRespond(withSuccess("{\"id\":\"email-1\"}", MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Redefinir senha")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ignore este e-mail")))
                .andRespond(withSuccess("{\"id\":\"email-2\"}", MediaType.APPLICATION_JSON));

        service.sendVerificationEmail("cliente@example.com",
                "https://garage.example/verify-email?token=opaque");
        service.sendPasswordResetEmail("cliente@example.com",
                "https://garage.example/reset-password?token=opaque");
        server.verify();
    }

    @Test
    void providerFailureBecomesGenericExceptionWithoutLeakingProviderResponseOrKey() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.resend.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ResendAccountEmailService service = new ResendAccountEmailService(
                builder.build(), TEST_KEY, "onboarding@resend.dev");
        server.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withUnauthorizedRequest().body("internal provider detail " + TEST_KEY));

        assertThatThrownBy(() -> service.sendVerificationEmail(
                "cliente@example.com", "https://garage.example/verify-email?token=opaque"))
                .isInstanceOf(AccountEmailDeliveryException.class)
                .hasMessage("Não foi possível enviar o e-mail da conta")
                .hasMessageNotContaining(TEST_KEY)
                .hasMessageNotContaining("internal provider detail");
        server.verify();
    }
}
