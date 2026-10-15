package com.garage.garageapi.integration.aliexpress;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class AliExpressTokenCipher {
    private final String configuredKey;
    private final SecureRandom random = new SecureRandom();
    public AliExpressTokenCipher(@Value("${aliexpress.token-encryption-key:}") String key) { configuredKey = key; }

    public String encrypt(String value, String purpose) {
        try {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce, purpose);
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getEncoder().encodeToString(ByteBuffer.allocate(12 + encrypted.length).put(nonce).put(encrypted).array());
        } catch (Exception ignored) { throw failure(); }
    }
    public String decrypt(String value, String purpose) {
        try {
            if (!value.startsWith("v1:")) throw failure();
            ByteBuffer data = ByteBuffer.wrap(Base64.getDecoder().decode(value.substring(3)));
            byte[] nonce = new byte[12]; data.get(nonce);
            byte[] encrypted = new byte[data.remaining()]; data.get(encrypted);
            return new String(cipher(Cipher.DECRYPT_MODE, nonce, purpose).doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ignored) { throw failure(); }
    }
    public void requireConfigured() { encrypt("configuration-check", "configuration-check"); }
    private Cipher cipher(int mode, byte[] nonce, String purpose) throws Exception {
        byte[] key = Base64.getDecoder().decode(configuredKey);
        if (key.length != 32) throw failure();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(purpose.getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
    private AliExpressIntegrationException failure() { return new AliExpressIntegrationException(AliExpressIntegrationException.Reason.NOT_CONFIGURED); }
}
