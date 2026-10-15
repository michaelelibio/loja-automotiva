package com.garage.garageapi.integration.aliexpress;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.node.ObjectNode;
import java.util.function.Consumer;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class AliExpressPreviewDiagnosticsTests {
    private static final String SENSITIVE = "synthetic-sensitive-value-never-log";
    private final AliExpressProductNormalizer normalizer = new AliExpressProductNormalizer();

    @ParameterizedTest(name="{0}")
    @MethodSource("failures")
    void identifiesFailureWithoutLoggingValues(String name, Consumer<ObjectNode> mutate,
            String stage, String field, String condition, CapturedOutput output) throws Exception {
        ObjectNode source = (ObjectNode) AliExpressProductNormalizerTests.source();
        source.withObject("ae_item_base_info_dto").put("detail", SENSITIVE
                + " access_token refresh_token authorization_code app_secret https://example.test/oauth/authorize?state=" + SENSITIVE);
        mutate.accept(source);
        assertThatThrownBy(() -> normalizer.normalize(source,"1005008752784588","US"))
                .isInstanceOfSatisfying(AliExpressIntegrationException.class, ex -> {
                    assertThat(ex.getReason()).isEqualTo(AliExpressIntegrationException.Reason.INCOMPLETE);
                    assertThat(ex.getMessage()).isEqualTo("Resposta AliExpress incompleta ou invalida; importacao cancelada.");
                }).hasNoCause();
        assertThat(output.getAll()).contains("AliExpress preview incomplete", "stage="+stage+";",
                        "field="+field+";", "condition="+condition+";")
                .doesNotContain(SENSITIVE,"https://example.test/oauth/authorize", "access_token refresh_token", "Automotive accessory example");
    }

    static Stream<Arguments> failures() {
        return Stream.of(
            failure("missing base",s->s.remove("ae_item_base_info_dto"),"base","product_id","required_value_missing_or_blank"),
            failure("wrong product",s->s.withObject("ae_item_base_info_dto").put("product_id",SENSITIVE),"base","product_id","product_id_mismatch"),
            failure("scalar expected",s->s.withObject("ae_item_base_info_dto").putObject("subject").put("secret",SENSITIVE),"base","subject","expected_scalar"),
            failure("long title",s->s.withObject("ae_item_base_info_dto").put("subject","x".repeat(1001)),"base","subject","value_too_long"),
            failure("blank title",s->s.withObject("ae_item_base_info_dto").put("subject"," "),"base","subject","required_value_missing_or_blank"),
            failure("long description",s->s.withObject("ae_item_base_info_dto").put("detail","x".repeat(200001)),"description","detail","description_too_long"),
            failure("image limit",s->s.withObject("ae_multimedia_info_dto").put("image_urls",java.util.stream.IntStream.range(0,101).mapToObj(i->"https://ae01.alicdn.com/"+i+".jpg").collect(java.util.stream.Collectors.joining(";"))),"main_images","image_urls","too_many_images"),
            failure("invalid wrapper",s->s.withObject("ae_item_sku_info_dtos").put("ae_item_sku_info_d_t_o",SENSITIVE),"variants","ae_item_sku_info_dtos","expected_array"),
            failure("no variants",s->s.remove("ae_item_sku_info_dtos"),"variants","ae_item_sku_info_dtos","no_variants"),
            failure("too many variants",s->{var arr=s.putArray("ae_item_sku_info_dtos");for(int i=0;i<1001;i++){var sku=arr.addObject();sku.put("sku_id","id"+i);sku.put("sku_price","1");sku.put("currency_code","USD");sku.put("sku_available_stock","0");}},"variants","ae_item_sku_info_dtos","too_many_variants"),
            failure("missing sku",s->sku(s,0).remove("sku_id"),"variant","sku_id","required_value_missing_or_blank"),
            failure("duplicate sku",s->sku(s,1).put("sku_id","201"),"variant","sku_id","duplicate_sku_id"),
            failure("long raw attributes",s->sku(s,0).put("sku_attr","x".repeat(501)),"variant","sku_attr","sku_attr_too_long"),
            failure("invalid property array",s->sku(s,0).withObject("ae_sku_property_dtos").put("ae_sku_property_d_t_o",SENSITIVE),"variant_properties","ae_sku_property_dtos","expected_array"),
            failure("long key",s->property(s).put("sku_property_id",SENSITIVE.repeat(6)),"variant_properties","attribute_key","attribute_key_too_long"),
            failure("long value",s->property(s).put("property_value_definition_name",SENSITIVE.repeat(40)),"variant_properties","property_value_definition_name","attribute_value_too_long"),
            failure("conflicting attribute",s->{var p=property(s).deepCopy();p.put("property_value_definition_name",SENSITIVE);((tools.jackson.databind.node.ArrayNode)sku(s,0).path("ae_sku_property_dtos").path("ae_sku_property_d_t_o")).add(p);},"variant_properties","property_value_definition_name","conflicting_attribute"),
            failure("invalid regular price",s->sku(s,0).put("sku_price",SENSITIVE),"variant_prices","sku_price","invalid_price_format"),
            failure("zero regular price",s->sku(s,0).put("sku_price","0"),"variant_prices","sku_price","non_positive_price"),
            failure("invalid offer price",s->sku(s,0).put("offer_sale_price",SENSITIVE),"variant_prices","offer_sale_price","invalid_price_format"),
            failure("invalid currency",s->sku(s,0).put("currency_code","ZZZ"),"variant_currency","currency_code","unexpected_exception"),
            failure("non integer stock",s->sku(s,0).put("sku_available_stock","1.5"),"variant_stock","sku_available_stock","unexpected_exception"),
            failure("overflow stock",s->sku(s,0).put("sku_available_stock","2147483648"),"variant_stock","sku_available_stock","unexpected_exception"),
            failure("negative stock",s->sku(s,0).put("sku_available_stock","-1"),"variant_stock","sku_available_stock","negative_stock"),
            failure("long sku code",s->sku(s,0).put("sku_code",SENSITIVE.repeat(5)),"variant","sku_code","value_too_long"),
            failure("mixed currencies",s->sku(s,1).put("currency_code","BRL"),"variants","currency_code","mixed_currencies"),
            failure("invalid product attributes",s->s.withObject("ae_item_properties").put("ae_item_property",SENSITIVE),"product_attributes","ae_item_properties","expected_array"),
            failure("invalid package scalar",s->s.withObject("package_info_dto").putObject("gross_weight").put("secret",SENSITIVE),"package_info","gross_weight","expected_scalar"),
            failure("wrong country",s->s.withObject("logistics_info_dto").put("ship_to_country",SENSITIVE),"logistics","ship_to_country","country_mismatch"),
            failure("long store value",s->s.withObject("ae_store_info").put("store_name",SENSITIVE.repeat(40)),"store","store_name","attribute_value_too_long")
        );
    }
    private static Arguments failure(String name, Consumer<ObjectNode> mutate,String stage,String field,String condition) {
        return Arguments.of(name,mutate,stage,field,condition);
    }
    private static ObjectNode sku(ObjectNode source,int index) {
        return (ObjectNode)source.path("ae_item_sku_info_dtos").path("ae_item_sku_info_d_t_o").get(index);
    }
    private static ObjectNode property(ObjectNode source) {
        return (ObjectNode)sku(source,0).path("ae_sku_property_dtos").path("ae_sku_property_d_t_o").get(0);
    }
    @Test void identifiesVariantAndPropertyIndices(CapturedOutput output) throws Exception {
        ObjectNode source=(ObjectNode)AliExpressProductNormalizerTests.source();
        sku(source,1).put("sku_id","201");
        assertThatThrownBy(()->normalizer.normalize(source,"1005008752784588","US")).isInstanceOf(AliExpressIntegrationException.class);
        assertThat(output.getAll()).contains("variantIndex=1; propertyIndex=-1");
    }
    @Test void fingerprintFailureDoesNotLogExceptionMessage(CapturedOutput output) throws Exception {
        var source=AliExpressProductNormalizerTests.source();
        try(var hash=mockStatic(AliExpressOAuthStateService.class)) {
            hash.when(()->AliExpressOAuthStateService.hash(anyString())).thenThrow(new IllegalStateException(SENSITIVE));
            assertThatThrownBy(()->normalizer.normalize(source,"1005008752784588","US")).isInstanceOf(AliExpressIntegrationException.class).hasNoCause();
            assertThat(output.getAll()).contains("stage=fingerprint; field=normalized_preview; condition=unexpected_exception", "exceptionType=IllegalStateException").doesNotContain(SENSITIVE);
        }
    }
}
