package com.garage.garageapi.integration.aliexpress;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;
class AliExpressProductInputTests {
    @ParameterizedTest
    @ValueSource(strings={"1005008752784588","https://pt.aliexpress.com/item/1005008752784588.html", " https://www.aliexpress.com/item/1005008752784588.html?spm=test#fragment "})
    void extractsIdsWithoutFetchingInput(String input) {
        assertThat(AliExpressProductInput.productId(input)).isEqualTo("1005008752784588");
    }
    @ParameterizedTest @NullSource
    @ValueSource(strings={"", "0", "abc", "123", "-1005008752784588", "http://pt.aliexpress.com/item/1005008752784588.html",
            "https://aliexpress.com.evil.test/item/1005008752784588.html", "https://127.0.0.1/item/1005008752784588.html",
            "https://169.254.169.254/item/1005008752784588.html", "https://evil.test@pt.aliexpress.com/item/1005008752784588.html",
            "https://pt.aliexpress.com:8080/item/1005008752784588.html", "https://s.click.aliexpress.com/e/test",
            "file:///etc/passwd", "https://pt.aliexpress.com/item/%31%30.html", "https://pt.aliexpress.com/redirect?url=http://localhost"})
    void rejectsInvalidAndSsrfInputs(String input) {
        assertThatThrownBy(() -> AliExpressProductInput.productId(input)).isInstanceOf(AliExpressIntegrationException.class);
    }
}
