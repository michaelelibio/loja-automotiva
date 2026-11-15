package com.garage.garageapi.integration.aliexpress;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class AliExpressSupplierCostsTests {
    @Test void queryUsesOnlyExplicitSupplierOrderAmountAndCurrency() {
        var mapper=tools.jackson.databind.json.JsonMapper.builder().build();
        var costs=AliExpressOrderQueryClient.costs(mapper.readTree("{\"order_amount\":{\"amount\":\"35.50\",\"currency_code\":\"BRL\"}}"));
        assertThat(costs.total()).isEqualByComparingTo("35.50");assertThat(costs.shippingTotal()).isNull();assertThat(costs.taxTotal()).isNull();
        assertThat(costs.warnings(BigDecimal.TEN)).anyMatch(w->w.contains("superior"));
        assertThat(AliExpressOrderQueryClient.costs(mapper.readTree("{\"order_amount\":{\"amount\":\"unknown\",\"currency_code\":\"BRL\"}}"))).isNull();
        assertThat(AliExpressOrderQueryClient.costs(mapper.readTree("{}"))).isNull();
    }
}
