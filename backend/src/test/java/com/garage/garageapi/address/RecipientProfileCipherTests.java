package com.garage.garageapi.address;

import com.garage.garageapi.address.recipient.RecipientProfileCipher;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RecipientProfileCipherTests {
    @Test void authenticatedEncryptionUsesRandomNoncesAndBindsOwnerAddressAndRecipient() {
        var cipher=new RecipientProfileCipher("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        String context="owner:address:recipient";
        String first=cipher.encrypt("synthetic-personal-data",context),second=cipher.encrypt("synthetic-personal-data",context);
        assertThat(first).isNotEqualTo(second).doesNotContain("synthetic-personal-data");
        assertThat(cipher.decrypt(first,context)).isEqualTo("synthetic-personal-data");
        assertThatThrownBy(()->cipher.decrypt(first,"other-owner:address:recipient")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->cipher.decrypt(first.substring(0,first.length()-5)+"AAAAA",context)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->new RecipientProfileCipher("").encrypt("synthetic-personal-data",context))
                .hasMessageNotContaining("synthetic-personal-data");
    }
}
