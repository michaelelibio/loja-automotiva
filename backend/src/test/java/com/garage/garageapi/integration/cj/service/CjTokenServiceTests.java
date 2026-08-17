package com.garage.garageapi.integration.cj.service;

import com.garage.garageapi.integration.cj.client.CjApiClient;
import com.garage.garageapi.integration.cj.client.CjTokenData;
import com.garage.garageapi.integration.cj.exception.CjIntegrationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CjTokenServiceTests {
    private static final Instant NOW = Instant.parse("2026-08-17T12:00:00Z");

    @Test
    void rejectsMissingApiKeyWithoutCallingCj() {
        CjApiClient client = mock(CjApiClient.class);
        CjTokenService service = service(client, "");

        assertThatThrownBy(service::getValidAccessToken)
                .isInstanceOf(CjIntegrationException.class)
                .hasMessage("Integração com a CJ não está configurada");
        verify(client, never()).authenticate(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void reusesValidAccessToken() {
        CjApiClient client = mock(CjApiClient.class);
        when(client.authenticate("API_KEY")).thenReturn(token("ACCESS", NOW.plusSeconds(3600),
                "REFRESH", NOW.plusSeconds(7200)));
        CjTokenService service = service(client, "API_KEY");

        assertThat(service.getValidAccessToken()).isEqualTo("ACCESS");
        assertThat(service.getValidAccessToken()).isEqualTo("ACCESS");
        verify(client, times(1)).authenticate("API_KEY");
    }

    @Test
    void refreshesExpiringAccessToken() {
        CjApiClient client = mock(CjApiClient.class);
        when(client.authenticate("API_KEY")).thenReturn(token("ACCESS", NOW.plusSeconds(3600),
                "REFRESH", NOW.plusSeconds(7200)));
        when(client.refresh("REFRESH")).thenReturn(token("RENEWED", NOW.plusSeconds(10800),
                "NEW_REFRESH", NOW.plusSeconds(14400)));
        CjTokenService service = service(client, "API_KEY");
        assertThat(service.getValidAccessToken()).isEqualTo("ACCESS");
        service.invalidateAccessToken("ACCESS");

        assertThat(service.getValidAccessToken()).isEqualTo("RENEWED");
        verify(client).refresh("REFRESH");
    }

    @Test
    void fallsBackToApiKeyWhenRefreshIsRejected() {
        CjApiClient client = mock(CjApiClient.class);
        when(client.authenticate("API_KEY"))
                .thenReturn(token("ACCESS", NOW.plusSeconds(3600), "REFRESH", NOW.plusSeconds(7200)))
                .thenReturn(token("NEW_ACCESS", NOW.plusSeconds(10800), "NEW_REFRESH", NOW.plusSeconds(14400)));
        when(client.refresh("REFRESH")).thenThrow(new CjIntegrationException("rejected",
                CjIntegrationException.Reason.AUTHENTICATION));
        CjTokenService service = service(client, "API_KEY");
        service.getValidAccessToken();
        service.invalidateAccessToken("ACCESS");

        assertThat(service.getValidAccessToken()).isEqualTo("NEW_ACCESS");
        verify(client, times(2)).authenticate("API_KEY");
    }

    private CjTokenService service(CjApiClient client, String apiKey) {
        return new CjTokenService(client, apiKey, Duration.ofMinutes(5),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private CjTokenData token(String access, Instant accessExpiry, String refresh,
                              Instant refreshExpiry) {
        return new CjTokenData(access, accessExpiry, refresh, refreshExpiry);
    }
}
