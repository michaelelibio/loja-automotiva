package com.garage.garageapi.integration.aliexpress;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(OutputCaptureExtension.class)
class AliExpressAuthServiceTests {
    private static final String SECRET = "synthetic-app-secret";
    private static final String ACCESS = "synthetic-access-token";
    private static final String REFRESH = "synthetic-refresh-token";
    private static final String CODE = "code+with&characters";
    private static final String SUCCESS = """
            {"code":"0","access_token":"synthetic-access-token","refresh_token":"synthetic-refresh-token",
             "expires_in":"86400","refresh_expires_in":172800,"expire_time":1700086400000,
             "refresh_token_valid_time":"1700172800000","user_id":"user-1","account_id":"account-1"}
            """;
    private MockRestServiceServer server;
    private AliExpressAuthService service;
    private ch.qos.logback.classic.Level previousClientLogLevel;
    private final ch.qos.logback.classic.Logger clientLogger =
            (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("org.springframework.web.client.DefaultRestClient");

    @BeforeEach
    void setUp() {
        previousClientLogLevel = clientLogger.getLevel();
        clientLogger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        var builder = RestClient.builder().baseUrl("https://api-sg.aliexpress.com/rest");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new AliExpressAuthService(builder.build(), "synthetic-app-key", SECRET,
                "https://example.test/api/integrations/aliexpress/callback",
                Clock.fixed(Instant.ofEpochMilli(1700000000000L), ZoneOffset.UTC));
    }

    @AfterEach
    void restoreClientLogging() {
        clientLogger.setLevel(previousClientLogLevel);
    }

    @Test
    void sendsSignedFormAndMapsTokensWithoutLoggingSensitiveValues(CapturedOutput output) {
        server.expect(requestTo("https://api-sg.aliexpress.com/rest/auth/token/create"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request).getBodyAsString();
                    Map<String, String> form = new HashMap<>();
                    for (String pair : body.split("&")) {
                        String[] parts = pair.split("=", 2);
                        form.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                                URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
                    }
                    assertThat(form).containsExactlyInAnyOrderEntriesOf(Map.of(
                            "app_key", "synthetic-app-key", "code", CODE,
                            "sign_method", "sha256", "timestamp", "1700000000000",
                            "sign", "9CF9C869C43B703667113A3655B13C172BDFC7EFBFA1545566C8140942EDC7BD"));
                    assertThat(body).doesNotContain(SECRET, "redirect_uri", "grant_type", "client_secret");
                    assertThat(request.getURI().getQuery()).isNull();
                }).andRespond(withSuccess(SUCCESS, MediaType.APPLICATION_JSON));
        var result = service.exchangeCode(CODE);
        assertThat(result.accessToken()).isEqualTo(ACCESS);
        assertThat(result.refreshToken()).isEqualTo(REFRESH);
        assertThat(result.expiresIn()).isEqualTo(86400L);
        assertThat(result.refreshExpiresIn()).isEqualTo(172800L);
        assertThat(result.expireTime()).isEqualTo(1700086400000L);
        assertThat(result.refreshTokenValidTime()).isEqualTo(1700172800000L);
        assertThat(result.userId()).isEqualTo("user-1");
        assertThat(result.accountId()).isEqualTo("account-1");
        assertThat(result.toString()).doesNotContain(ACCESS, REFRESH, "user-1");
        assertSafeLogs(output);
        server.verify();
    }

    @Test
    void httpErrorDoesNotLeakUpstreamBody(CapturedOutput output) {
        server.expect(anything()).andRespond(withBadRequest()
                .body(SECRET + ACCESS + REFRESH).contentType(MediaType.TEXT_PLAIN));
        assertSafeFailure(output);
    }

    @Test
    void providerErrorIsRejectedEvenWithHttp200(CapturedOutput output) {
        server.expect(anything()).andRespond(withSuccess(
                "{\"code\":\"IncompleteSignature\",\"type\":\"ISV\",\"message\":\"" + SECRET + ACCESS + REFRESH + "\"}",
                MediaType.APPLICATION_JSON));
        assertSafeFailure(output);
    }

    @Test
    void missingAccessTokenIsNotSuccessful(CapturedOutput output) {
        server.expect(anything()).andRespond(withSuccess("{\"code\":0}", MediaType.APPLICATION_JSON));
        assertSafeFailure(output);
    }

    @Test
    void malformedResponseIsSafe(CapturedOutput output) {
        server.expect(anything()).andRespond(withSuccess(SECRET + ACCESS, MediaType.APPLICATION_JSON));
        assertSafeFailure(output);
    }

    @Test
    void transportErrorIsSafe(CapturedOutput output) {
        server.expect(anything()).andRespond(withException(new java.io.IOException(SECRET + ACCESS + REFRESH)));
        assertSafeFailure(output);
    }

    @Test
    void absentConfigurationMakesNoHttpRequest() {
        var unconfigured = new AliExpressAuthService(RestClient.create(), "", "", "", Clock.systemUTC());
        assertThatThrownBy(() -> unconfigured.exchangeCode(CODE)).isInstanceOf(AliExpressAuthException.class);
        server.verify();
    }

    private void assertSafeFailure(CapturedOutput output) {
        assertThatThrownBy(() -> service.exchangeCode(CODE)).isInstanceOf(AliExpressAuthException.class)
                .hasMessage("Nao foi possivel concluir a autorizacao AliExpress. Tente autorizar novamente.")
                .hasNoCause();
        assertSafeLogs(output);
        server.verify();
    }

    private void assertSafeLogs(CapturedOutput output) {
        assertThat(output.getAll()).doesNotContain(SECRET, ACCESS, REFRESH, CODE);
    }
}
