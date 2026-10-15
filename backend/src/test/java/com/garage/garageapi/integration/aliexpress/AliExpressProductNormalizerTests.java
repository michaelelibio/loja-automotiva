package com.garage.garageapi.integration.aliexpress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AliExpressProductNormalizerTests {
    static final JsonMapper JSON = JsonMapper.builder().build();
    static JsonNode source() throws Exception {
        try(var stream = AliExpressProductNormalizerTests.class.getResourceAsStream("/aliexpress-product.json")) { return JSON.readTree(stream); }
    }
    private final AliExpressProductNormalizer normalizer = new AliExpressProductNormalizer();
    @Test
    void mapsImagesPricesSkusStockAndOriginalAttributes() throws Exception {
        var preview=normalizer.normalize(source(),"1005008752784588","US");
        assertThat(preview.images()).hasSize(6);
        assertThat(preview.description()).isEqualTo("Test description");
        assertThat(preview.variants()).hasSize(4);
        var first=preview.variants().get(0);
        assertThat(first.skuId()).isEqualTo("201");
        assertThat(first.price()).isEqualByComparingTo("8.90");
        assertThat(first.regularPrice()).isEqualByComparingTo("10.00");
        assertThat(first.availableStock()).isEqualTo(10);
        assertThat(first.images()).containsExactly("https://ae01.alicdn.com/black.jpg");
        assertThat(first.attributes()).containsEntry("14:valueId","771").containsEntry("14","Black");
        assertThat(preview.variants().get(2).availableStock()).isZero();
        assertThat(preview.warnings()).anyMatch(w -> w.contains("inconsistentes"));
        assertThat(preview.domesticShippingConfirmed()).isFalse();
        assertThat(preview.packageInfo()).containsEntry("gross_weight","0.050");
        assertThat(preview.fingerprint()).hasSize(64);
        assertThat(preview.fingerprint()).isEqualTo(normalizer.normalize(source(),"1005008752784588","US").fingerprint());
    }
    @Test
    void acceptsArrayInsteadOfLegacyWrappers() throws Exception {
        var source=(ObjectNode)source();
        source.set("ae_item_sku_info_dtos",source.path("ae_item_sku_info_dtos").path("ae_item_sku_info_d_t_o"));
        source.set("ae_item_properties",source.path("ae_item_properties").path("ae_item_property"));
        assertThat(normalizer.normalize(source,"1005008752784588","US").variants()).hasSize(4);
    }
    @Test
    void normalizesAndFiltersImageUrls() {
        List<String> warnings=new ArrayList<>();
        assertThat(AliExpressProductNormalizer.images(" //ae01.alicdn.com/1.jpg ;http://ae01.alicdn.com/1.jpg;https://evil.test/image;http://127.0.0.1/private;javascript:alert(1)",warnings))
                .containsExactly("https://ae01.alicdn.com/1.jpg");
        assertThat(warnings).isNotEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"-1","0","NaN","1,23","1e4","1.23456",""})
    void rejectsInvalidPrices(String value) {
        assertThatThrownBy(() -> AliExpressProductNormalizer.money(value)).isInstanceOf(AliExpressIntegrationException.class);
    }
    @Test
    void missingSkuOrStockIsNotInvented() throws Exception {
        var source=source();
        ((ObjectNode)source.path("ae_item_sku_info_dtos").path("ae_item_sku_info_d_t_o").get(0)).remove("sku_id");
        assertThatThrownBy(() -> normalizer.normalize(source,"1005008752784588","US")).isInstanceOf(AliExpressIntegrationException.class);
        var other=source();
        ((ObjectNode)other.path("ae_item_sku_info_dtos").path("ae_item_sku_info_d_t_o").get(0)).remove("sku_available_stock");
        assertThatThrownBy(() -> normalizer.normalize(other,"1005008752784588","US")).isInstanceOf(AliExpressIntegrationException.class);
    }
    @Test
    void rejectsCountryMismatchAndWrongProduct() throws Exception {
        var source=source();
        assertThatThrownBy(() -> normalizer.normalize(source,"99999999","US")).isInstanceOf(AliExpressIntegrationException.class);
        assertThatThrownBy(() -> normalizer.normalize(source,"1005008752784588","BR")).isInstanceOf(AliExpressIntegrationException.class);
    }
    @Test
    void rejectsMixedCurrenciesAndDuplicateSkuIds() throws Exception {
        var source=source();
        ((ObjectNode)source.path("ae_item_sku_info_dtos").path("ae_item_sku_info_d_t_o").get(0)).put("currency_code","BRL");
        assertThatThrownBy(() -> normalizer.normalize(source,"1005008752784588","US")).isInstanceOf(AliExpressIntegrationException.class);
        var other=source();
        ((ObjectNode)other.path("ae_item_sku_info_dtos").path("ae_item_sku_info_d_t_o").get(1)).put("sku_id","201");
        assertThatThrownBy(() -> normalizer.normalize(other,"1005008752784588","US")).isInstanceOf(AliExpressIntegrationException.class);
    }
}
