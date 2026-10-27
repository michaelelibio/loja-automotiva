package com.garage.garageapi.integration.aliexpress;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.garage.garageapi.integration.aliexpress.AliExpressFreightDiagnosticResult.Status.*;

/** Real token service, mocked storage/cipher/provider: verifies the complete credential path. */
class AliExpressTerritorialDiagnosticServiceTests {
    final AliExpressAuthorizationRepository repository=mock(AliExpressAuthorizationRepository.class);
    final AliExpressTokenCipher cipher=mock(AliExpressTokenCipher.class);
    final AliExpressAuthService auth=mock(AliExpressAuthService.class);
    final AliExpressTerritorialClient client=mock(AliExpressTerritorialClient.class);
    final String id=AliExpressOAuthStateService.hash("synthetic-key");
    final AliExpressTokenService tokens=new AliExpressTokenService(repository,cipher,auth,"synthetic-key");
    final AliExpressTerritorialDiagnosticService service=new AliExpressTerritorialDiagnosticService(tokens,client);

    void stored(Instant expiry) {
        var row=new AliExpressAuthorization(id);
        row.update("encrypted-access","encrypted-refresh",expiry,Instant.now().plusSeconds(7200));
        when(repository.findById(id)).thenReturn(Optional.of(row));
    }
    @Test void storedTokenIsReadAndDecryptedOnlyOnceWithoutRefreshLockOrWrites() {
        stored(Instant.now().plusSeconds(3600));
        when(cipher.decrypt("encrypted-access",id+":access")).thenReturn("synthetic-persisted-token");
        var result=AliExpressTerritorialDiagnosticResult.of(SUCCESS,"TERRITORIAL_QUERY_SUCCEEDED",true,"BR",List.of(),List.of());
        when(client.query("synthetic-persisted-token")).thenReturn(result);
        assertThat(service.run()).isSameAs(result);
        var calls=inOrder(repository,cipher,client);
        calls.verify(repository).findById(id);calls.verify(cipher).decrypt("encrypted-access",id+":access");
        calls.verify(client).query("synthetic-persisted-token");
        verifyNoMoreInteractions(repository,cipher,client);verifyNoInteractions(auth);
    }
    @Test void expiredStoredAccessCannotRefreshEvenWithValidRefreshCredential() {
        stored(Instant.now().minusSeconds(1));
        assertThat(service.run().safeCode()).isEqualTo("LOCAL_TOKEN_UNAVAILABLE");
        verify(repository).findById(id);verifyNoMoreInteractions(repository);verifyNoInteractions(cipher,auth,client);
    }
    @Test void missingAuthorizationCannotGenerateAnAuthorizationOrCallProvider() {
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThat(service.run().safeCode()).isEqualTo("LOCAL_TOKEN_UNAVAILABLE");
        verify(repository).findById(id);verifyNoMoreInteractions(repository);verifyNoInteractions(cipher,auth,client);
    }
    @Test void decryptionFailureCannotReplaceEncryptionKeyOrCredential() {
        stored(Instant.now().plusSeconds(3600));
        when(cipher.decrypt("encrypted-access",id+":access")).thenThrow(new IllegalStateException("synthetic-private-detail"));
        var result=service.run();
        assertThat(result.safeCode()).isEqualTo("LOCAL_TOKEN_UNAVAILABLE");
        assertThat(result.toString()).doesNotContain("synthetic-private-detail","encrypted-access");
        verify(repository).findById(id);verify(cipher).decrypt("encrypted-access",id+":access");
        verifyNoMoreInteractions(repository,cipher);verifyNoInteractions(auth,client);
    }
}
