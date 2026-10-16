package com.garage.garageapi.integration.aliexpress;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AliExpressAuthorizationDiagnosticsTests {
    final AliExpressAuthorizationRepository repository = mock(AliExpressAuthorizationRepository.class);
    final AliExpressTokenCipher cipher = mock(AliExpressTokenCipher.class);
    final AliExpressAuthorizationDiagnostics service = new AliExpressAuthorizationDiagnostics(repository, cipher, "test-app", "test-secret");
    final String id = AliExpressOAuthStateService.hash("test-app");

    @Test void missingConfigurationNeverReadsStorage() {
        var report = new AliExpressAuthorizationDiagnostics(repository,cipher,"","").inspect();
        assertThat(report.accessTokenState()).isEqualTo("APPLICATION_NOT_CONFIGURED");
        verifyNoInteractions(repository,cipher);
    }
    @Test void missingAuthorizationDoesNotClaimProviderPermission() {
        when(repository.findById(id)).thenReturn(Optional.empty());
        var report = service.inspect();
        assertThat(report.accessTokenState()).isEqualTo("AUTHORIZATION_NOT_FOUND");
        assertThat(report.freightPermission()).isEqualTo("NOT_VERIFIED");
        verifyNoInteractions(cipher);
    }
    @Test void expiredTokenIsNeverRefreshedOrDecrypted() {
        var row = new AliExpressAuthorization(id);
        row.update("encrypted", "refresh-encrypted", Instant.now().minusSeconds(1), Instant.now().plusSeconds(3600));
        when(repository.findById(id)).thenReturn(Optional.of(row));
        var report = service.inspect();
        assertThat(report.accessTokenState()).isEqualTo("EXPIRED_OR_EXPIRING");
        assertThat(report.refreshAttempted()).isFalse();
        verifyNoInteractions(cipher);
        verify(repository,never()).saveAndFlush(any());
    }
    @Test void usableTokenIsNeverReturnedAndDoesNotProveFreightPermission() {
        var row = new AliExpressAuthorization(id);
        row.update("encrypted", null, Instant.now().plusSeconds(3600), null);
        when(repository.findById(id)).thenReturn(Optional.of(row));
        when(cipher.decrypt("encrypted", id + ":access")).thenReturn("private-access-value");
        var report = service.inspect();
        assertThat(report.accessTokenState()).isEqualTo("LOCALLY_USABLE");
        assertThat(report.providerValidation()).isEqualTo("NOT_VERIFIED");
        assertThat(report.freightPermission()).isEqualTo("NOT_VERIFIED");
        assertThat(report.toString()).doesNotContain("private-access-value","test-secret","encrypted");
        verify(repository,never()).saveAndFlush(any());
    }
    @Test void storageAndCipherFailuresAreRedacted() {
        when(repository.findById(id)).thenThrow(new IllegalStateException("sensitive-database-details"));
        assertThat(service.inspect().accessTokenState()).isEqualTo("STORAGE_UNAVAILABLE");
        var row = new AliExpressAuthorization(id);
        row.update("encrypted", null, Instant.now().plusSeconds(3600), null);
        doReturn(Optional.of(row)).when(repository).findById(id);
        when(cipher.decrypt(anyString(),anyString())).thenThrow(new IllegalStateException("sensitive-key-details"));
        assertThat(service.inspect().accessTokenState()).isEqualTo("UNREADABLE");
    }
}
