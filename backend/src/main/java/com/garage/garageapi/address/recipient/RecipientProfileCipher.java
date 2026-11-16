package com.garage.garageapi.address.recipient;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Dedicated key and address/owner-bound AEAD; never logs plaintext, keys or crypto errors. */
@Component
public class RecipientProfileCipher {
    private final String key;
    private final SecureRandom random=new SecureRandom();
    public RecipientProfileCipher(@Value("${app.recipient.encryption-key:}") String key) { this.key=key; }
    public String encrypt(String value,String context) {
        try {
            byte[] nonce=new byte[12];random.nextBytes(nonce);
            byte[] encrypted=cipher(Cipher.ENCRYPT_MODE,nonce,context).doFinal(value.getBytes(StandardCharsets.UTF_8));
            return "v1:"+Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length+encrypted.length).put(nonce).put(encrypted).array());
        } catch(Exception ignored) { throw unavailable(); }
    }
    public String decrypt(String value,String context) {
        try {
            if(!value.startsWith("v1:")||value.length()>2048)throw unavailable();
            var data=ByteBuffer.wrap(Base64.getDecoder().decode(value.substring(3)));
            byte[] nonce=new byte[12];data.get(nonce);byte[] encrypted=new byte[data.remaining()];data.get(encrypted);
            return new String(cipher(Cipher.DECRYPT_MODE,nonce,context).doFinal(encrypted),StandardCharsets.UTF_8);
        } catch(Exception ignored) { throw unavailable(); }
    }
    private Cipher cipher(int mode,byte[] nonce,String context) throws Exception {
        byte[] bytes=Base64.getDecoder().decode(key);if(bytes.length!=32)throw unavailable();
        var cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode,new SecretKeySpec(bytes,"AES"),new GCMParameterSpec(128,nonce));
        cipher.updateAAD(("ingarage:recipient:v1:"+context).getBytes(StandardCharsets.UTF_8));return cipher;
    }
    static org.springframework.web.server.ResponseStatusException unavailable() {
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "Não foi possível acessar os dados salvos do destinatário. Tente novamente mais tarde.");
    }
}
