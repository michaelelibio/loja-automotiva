package com.garage.garageapi.integration.cj.service;

import com.garage.garageapi.integration.cj.client.CjApiClient;
import com.garage.garageapi.integration.cj.dto.CjProductVariantsResponse;
import com.garage.garageapi.integration.cj.exception.CjIntegrationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CjProductServiceTests {
    @Mock CjApiClient client;
    @Mock CjTokenService tokenService;

    @Test
    void renewsTokenOnceWhenVariantQueryIsRejected() {
        CjProductVariantsResponse expected = new CjProductVariantsResponse("PID-1", List.of());
        when(tokenService.getValidAccessToken()).thenReturn("OLD", "NEW");
        when(client.getProductVariants("OLD", "PID-1")).thenThrow(new CjIntegrationException(
                "Autenticacao recusada", CjIntegrationException.Reason.AUTHENTICATION));
        when(client.getProductVariants("NEW", "PID-1")).thenReturn(expected);

        CjProductVariantsResponse result = new CjProductService(client, tokenService)
                .getVariants("PID-1");

        assertThat(result).isEqualTo(expected);
        verify(tokenService).invalidateAccessToken("OLD");
        verify(client).getProductVariants("NEW", "PID-1");
    }
}
