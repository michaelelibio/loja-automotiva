package com.garage.garageapi.shared.json;

import com.garage.garageapi.order.dto.CreateOrderRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonInputSafetyConfigurationTests {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withUserConfiguration(JsonInputSafetyConfiguration.class);

    @Test
    void checkoutAcceptsExactJsonNumbersAndKeepsOtherTextContracts() {
        context.run(app -> {
            ObjectMapper mapper = app.getBean(ObjectMapper.class);
            for (String amount : new String[] {"10", "10.50", "0.01", "1e2"}) {
                CreateOrderRequest request = mapper.readValue(body(amount), CreateOrderRequest.class);
                assertThat(request.expectedTotal()).isEqualByComparingTo(new BigDecimal(amount));
            }
            assertThat(mapper.readTree("{\"supplierPrice\":\"10.50\"}").path("supplierPrice").asString())
                    .isEqualTo("10.50");
        });
    }

    @Test
    void checkoutRejectsNumericStringsBeforeAnyNumberCoercion() {
        context.run(app -> {
            ObjectMapper mapper = app.getBean(ObjectMapper.class);
            for (String text : new String[] {"10.50", "", "1".repeat(8_192) + "x"}) {
                assertThatThrownBy(() -> mapper.readValue(body("\"" + text + "\""), CreateOrderRequest.class))
                        .isInstanceOf(JacksonException.class)
                        .hasMessageContaining("expectedTotal deve ser um número JSON");
            }
            for (String value : new String[] {"true", "[]", "{}"}) {
                assertThatThrownBy(() -> mapper.readValue(body(value), CreateOrderRequest.class))
                        .isInstanceOf(JacksonException.class);
            }
        });
    }

    @Test
    void bootMapperRejectsOversizedNumericTokensAndDeepDocuments() {
        context.run(app -> {
            ObjectMapper mapper = app.getBean(ObjectMapper.class);
            assertThatThrownBy(() -> mapper.readValue(body("9".repeat(129)), CreateOrderRequest.class))
                    .isInstanceOf(JacksonException.class)
                    .hasRootCauseInstanceOf(StreamConstraintsException.class);
            assertThatThrownBy(() -> mapper.readTree("[".repeat(65) + "0" + "]".repeat(65)))
                    .isInstanceOf(StreamConstraintsException.class);
        });
    }

    private static String body(String amount) {
        return "{\"addressId\":1,\"items\":[{\"productId\":1,\"quantity\":1}],\"expectedTotal\":" + amount + "}";
    }
}
