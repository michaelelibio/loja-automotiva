package com.garage.garageapi.integration.aliexpress;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import java.time.Instant;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"aliexpress.app-key=synthetic-test-key",
        "aliexpress.callback-url=https://example.test/api/integrations/aliexpress/callback",
        "aliexpress.token-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AliExpressTokenPersistenceTests {
    @Autowired MockMvc mvc;
    @Autowired AliExpressTokenService tokens;
    @Autowired AliExpressAuthorizationRepository authorizations;
    @Autowired AliExpressOAuthStateRepository stateRepository;
    @Autowired AliExpressOAuthStateService states;
    @MockitoBean AliExpressAuthService auth;
    @BeforeEach void clean() { authorizations.deleteAll();stateRepository.deleteAll();reset(auth); }
    @Test void persistsOnlyCiphertextAndReturnsAccessWhenValid() {
        tokens.store(data("synthetic-access","synthetic-refresh"));
        var stored=authorizations.findAll().get(0);
        assertThat(stored.accessCiphertext()).startsWith("v1:").doesNotContain("synthetic-access");
        assertThat(stored.refreshCiphertext()).startsWith("v1:").doesNotContain("synthetic-refresh");
        assertThat(tokens.accessToken()).isEqualTo("synthetic-access");verifyNoInteractions(auth);
    }
    @Test void renewsExpiredAccessAndDoesNotExtendRefreshWindow() {
        tokens.store(data("synthetic-access","synthetic-refresh"));
        var row=authorizations.findAll().get(0); Instant refreshExpiry=row.refreshExpiresAt();
        row.update(row.accessCiphertext(),row.refreshCiphertext(),Instant.now().minusSeconds(1),refreshExpiry);authorizations.saveAndFlush(row);
        when(auth.refreshToken("synthetic-refresh")).thenReturn(data("rotated-access","rotated-refresh"));
        assertThat(tokens.accessToken()).isEqualTo("rotated-access");
        assertThat(tokens.accessToken()).isEqualTo("rotated-access");
        assertThat(authorizations.findAll().get(0).refreshExpiresAt()).isEqualTo(refreshExpiry);
        verify(auth,times(1)).refreshToken("synthetic-refresh");
    }
    @Test void expiredRefreshRequiresReauthorizationWithoutApiCalls() {
        tokens.store(data("synthetic-access","synthetic-refresh"));
        var row=authorizations.findAll().get(0);
        row.update(row.accessCiphertext(),row.refreshCiphertext(),Instant.EPOCH,Instant.EPOCH);authorizations.saveAndFlush(row);
        assertThatThrownBy(tokens::accessToken).isInstanceOfSatisfying(AliExpressIntegrationException.class,
                e->assertThat(e.getReason()).isEqualTo(AliExpressIntegrationException.Reason.AUTHORIZATION_REQUIRED));verifyNoInteractions(auth);
    }
    @Test void missingAuthorizationDoesNotUseExplorerCredentials() {
        assertThatThrownBy(tokens::accessToken).isInstanceOf(AliExpressIntegrationException.class);verifyNoInteractions(auth);
    }
    @Test void zeroRefreshDurationCannotBeOverriddenByAbsoluteTimestamp() {
        tokens.store(new AliExpressTokenData("synthetic-access","synthetic-refresh",3600L,0L,null,Instant.now().plusSeconds(7200).toEpochMilli(),null,null));
        var row=authorizations.findAll().get(0);
        row.update(row.accessCiphertext(),row.refreshCiphertext(),Instant.EPOCH,row.refreshExpiresAt());authorizations.saveAndFlush(row);
        assertThatThrownBy(tokens::accessToken).isInstanceOf(AliExpressIntegrationException.class);verifyNoInteractions(auth);
    }
    @Test void invalidRefreshAccessExpirationLogsOnlyFixedField(CapturedOutput output) {
        assertInvalidExpiration(false,output);
    }
    @Test void invalidRefreshTokenExpirationLogsOnlyFixedField(CapturedOutput output) {
        assertInvalidExpiration(true,output);
    }
    private void assertInvalidExpiration(boolean refresh,CapturedOutput output) {
        tokens.store(data("synthetic-access","synthetic-refresh"));
        var row=authorizations.findAll().get(0);
        row.update(row.accessCiphertext(),row.refreshCiphertext(),Instant.EPOCH,row.refreshExpiresAt());authorizations.saveAndFlush(row);
        when(auth.refreshToken("synthetic-refresh")).thenReturn(new AliExpressTokenData("synthetic-sensitive-access","synthetic-sensitive-refresh",
                refresh?3600L:Long.MAX_VALUE,refresh?Long.MAX_VALUE:7200L,null,null,null,null));
        assertThatThrownBy(tokens::accessToken).isInstanceOfSatisfying(AliExpressIntegrationException.class,
                e->assertThat(e.getReason()).isEqualTo(AliExpressIntegrationException.Reason.INCOMPLETE)).hasNoCause();
        assertThat(output.getAll()).contains("stage=token_expiry; field="+(refresh?"refresh_token_expiration":"access_token_expiration")+"; condition=invalid_expiration")
                .doesNotContain("synthetic-sensitive-access","synthetic-sensitive-refresh");
        assertThat(authorizations.findAll().get(0).accessCiphertext()).isEqualTo(row.accessCiphertext());
    }
    @Test void rejectedRefreshDoesNotExposeOrReplaceTokens() {
        tokens.store(data("synthetic-access","synthetic-refresh"));
        var row=authorizations.findAll().get(0);
        row.update(row.accessCiphertext(),row.refreshCiphertext(),Instant.EPOCH,row.refreshExpiresAt());authorizations.saveAndFlush(row);
        when(auth.refreshToken(anyString())).thenThrow(new AliExpressAuthException());
        assertThatThrownBy(tokens::accessToken).isInstanceOf(AliExpressIntegrationException.class).hasNoCause();
        assertThat(authorizations.findAll().get(0).accessCiphertext()).isEqualTo(row.accessCiphertext());
    }
    @Test void concurrentRequestsRefreshOnlyOnce() throws Exception {
        tokens.store(data("synthetic-access","synthetic-refresh"));
        var row=authorizations.findAll().get(0);
        row.update(row.accessCiphertext(),row.refreshCiphertext(),Instant.EPOCH,row.refreshExpiresAt());authorizations.saveAndFlush(row);
        when(auth.refreshToken("synthetic-refresh")).thenReturn(data("rotated-access","rotated-refresh"));
        var first=CompletableFuture.supplyAsync(tokens::accessToken);
        var second=CompletableFuture.supplyAsync(tokens::accessToken);
        assertThat(first.get(5,TimeUnit.SECONDS)).isEqualTo("rotated-access");
        assertThat(second.get(5,TimeUnit.SECONDS)).isEqualTo("rotated-access");verify(auth,times(1)).refreshToken(anyString());
    }
    @Test void oauthStateIsHashedExpiresAndIsSingleUse() {
        var authorization=states.start();
        String url=authorization.authorizationUrl();
        assertThat(url).startsWith("https://api-sg.aliexpress.com/oauth/authorize?");
        String state=java.util.Arrays.stream(URI.create(url).getQuery().split("&")).filter(v->v.startsWith("state="))
                .map(v->URLDecoder.decode(v.substring(6),StandardCharsets.UTF_8)).findFirst().orElseThrow();
        assertThat(authorization.toString()).doesNotContain(state);
        assertThat(stateRepository.findAll()).hasSize(1);
        assertThat(stateRepository.existsById(state)).isFalse();
        states.consume(state);
        assertThatThrownBy(()->states.consume(state)).isInstanceOf(AliExpressIntegrationException.class);
        assertThatThrownBy(()->states.consume("wrong-state")).isInstanceOf(AliExpressIntegrationException.class);
        String expired="a".repeat(43);
        stateRepository.saveAndFlush(new AliExpressOAuthState(AliExpressOAuthStateService.hash(expired),Instant.EPOCH));
        assertThatThrownBy(()->states.consume(expired)).isInstanceOf(AliExpressIntegrationException.class);
    }
    @Test void callbackPersistsEncryptedTokensOnlyForFreshAdministrativeState(CapturedOutput output) throws Exception {
        String url=states.start().authorizationUrl();
        String state=java.util.Arrays.stream(URI.create(url).getQuery().split("&")).filter(v->v.startsWith("state="))
                .map(v->v.substring(6)).findFirst().orElseThrow();
        when(auth.exchangeCode("synthetic-code")).thenReturn(data("callback-access","callback-refresh"));
        mvc.perform(get("/api/integrations/aliexpress/callback").param("code","synthetic-code").param("state",state))
                .andExpect(status().isOk()).andExpect(content().string("Autorizacao AliExpress concluida com sucesso."));
        assertThat(tokens.accessToken()).isEqualTo("callback-access");
        assertThat(authorizations.findAll().get(0).accessCiphertext()).doesNotContain("callback-access");
        mvc.perform(get("/api/integrations/aliexpress/callback").param("code","synthetic-code").param("state",state))
                .andExpect(status().isBadRequest());
        verify(auth,times(1)).exchangeCode("synthetic-code");
        assertThat(output.getAll()).doesNotContain("callback-access","callback-refresh","synthetic-code",state);
    }
    static AliExpressTokenData data(String access,String refresh) {
        return new AliExpressTokenData(access,refresh,3600L,7200L,null,null,null,null);
    }
}
