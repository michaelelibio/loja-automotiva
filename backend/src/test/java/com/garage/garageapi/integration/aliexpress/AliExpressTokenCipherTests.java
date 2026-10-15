package com.garage.garageapi.integration.aliexpress;
import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;
class AliExpressTokenCipherTests {
    static final String KEY=Base64.getEncoder().encodeToString(new byte[32]); // synthetic test-only key
    @Test void authenticatesPurposeAndUsesRandomNonces() {
        var cipher=new AliExpressTokenCipher(KEY);
        String first=cipher.encrypt("synthetic-token","access");
        String second=cipher.encrypt("synthetic-token","access");
        assertThat(first).isNotEqualTo(second).doesNotContain("synthetic-token");
        assertThat(cipher.decrypt(first,"access")).isEqualTo("synthetic-token");
        assertThatThrownBy(()->cipher.decrypt(first,"refresh")).isInstanceOf(AliExpressIntegrationException.class).hasNoCause();
    }
    @Test void rejectsTamperingAndAbsentKey() {
        var cipher=new AliExpressTokenCipher(KEY);
        String encrypted=cipher.encrypt("synthetic-token","access");
        byte[] bytes=Base64.getDecoder().decode(encrypted.substring(3));bytes[14]^=1;
        assertThatThrownBy(()->cipher.decrypt("v1:"+Base64.getEncoder().encodeToString(bytes),"access")).isInstanceOf(AliExpressIntegrationException.class);
        assertThatThrownBy(()->new AliExpressTokenCipher("").requireConfigured()).isInstanceOf(AliExpressIntegrationException.class);
    }
}
