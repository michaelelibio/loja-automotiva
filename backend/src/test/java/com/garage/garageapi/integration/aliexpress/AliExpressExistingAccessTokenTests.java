package com.garage.garageapi.integration.aliexpress;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AliExpressExistingAccessTokenTests {
    final AliExpressAuthorizationRepository repository=mock(AliExpressAuthorizationRepository.class);
    final AliExpressTokenCipher cipher=mock(AliExpressTokenCipher.class);
    final AliExpressAuthService auth=mock(AliExpressAuthService.class);
    final AliExpressTokenService tokens=new AliExpressTokenService(repository,cipher,auth,"synthetic-key");
    final String id=AliExpressOAuthStateService.hash("synthetic-key");
    @Test void unexpiredTokenIsReadWithoutLockRefreshOrPersistence() {
        var row=new AliExpressAuthorization(id);row.update("encrypted","refresh",Instant.now().plusSeconds(3600),Instant.now().plusSeconds(7200));
        when(repository.findById(id)).thenReturn(Optional.of(row));
        when(cipher.decrypt("encrypted",id+":access")).thenReturn("synthetic-token");
        assertThat(tokens.existingAccessToken()).isEqualTo("synthetic-token");
        verify(repository).findById(id);verifyNoMoreInteractions(repository);verifyNoInteractions(auth);
    }
    @Test void expiredTokenIsRejectedEvenWhenRefreshIsAvailable() {
        var row=new AliExpressAuthorization(id);row.update("encrypted","refresh",Instant.now().minusSeconds(1),Instant.now().plusSeconds(7200));
        when(repository.findById(id)).thenReturn(Optional.of(row));
        assertThatThrownBy(tokens::existingAccessToken).isInstanceOf(AliExpressIntegrationException.class);
        verifyNoInteractions(auth,cipher);verify(repository).findById(id);verifyNoMoreInteractions(repository);
    }
}
