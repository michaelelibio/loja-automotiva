package com.garage.garageapi.auth.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class CorsConfigurationTests {
    @Test
    void trimsSplitsNormalizesAndDeduplicatesExplicitOrigins() {
        assertThat(SecurityConfig.parseAllowedOrigins(
                " https://ingarage.com.br , https://www.ingarage.com.br/ , https://ingarage.com.br "))
                .containsExactly("https://ingarage.com.br", "https://www.ingarage.com.br");
    }

    @Test
    void localOriginsCanIncludePorts() {
        assertThat(SecurityConfig.parseAllowedOrigins("http://localhost:3000,http://127.0.0.1:3000"))
                .containsExactly("http://localhost:3000", "http://127.0.0.1:3000");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", ",", "https://ingarage.com.br,", ",https://ingarage.com.br",
            "https://ingarage.com.br, ,https://www.ingarage.com.br", "*", "https://*.ingarage.com.br",
            "ingarage.com.br", "ftp://ingarage.com.br", "https://ingarage.com.br/login",
            "https://ingarage.com.br?x=1", "https://ingarage.com.br#fragment",
            "https://user:password@ingarage.com.br", "https://ingarage.com.br:0",
            "https://ingarage.com.br:65536", "https://ingarage.com.br:invalid", "\"https://ingarage.com.br\""})
    void rejectsInvalidOrEmptyEntries(String value) {
        assertThatThrownBy(() -> SecurityConfig.parseAllowedOrigins(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CORS allowed-origins")
                .hasNoCause();
    }

    @Test
    void configurationErrorsDoNotEchoCredentials() {
        assertThatThrownBy(() -> SecurityConfig.parseAllowedOrigins("https://user:synthetic-password@example.test"))
                .hasMessageNotContaining("synthetic-password");
    }
}
