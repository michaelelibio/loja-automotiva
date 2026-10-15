package com.garage.garageapi.integration.aliexpress;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.util.*;
import static com.garage.garageapi.integration.aliexpress.AliExpressIntegrationException.Reason.*;

@Component
public class AliExpressProductNormalizer {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AliExpressProductNormalizer.class);
    public AliExpressProductPreview normalize(JsonNode result, String requestedId, String country) {
        Diagnostic diagnostic = new Diagnostic("result", "$");
        try {
            diagnostic.stage = "base";
            diagnostic.field = "ae_item_base_info_dto";
            JsonNode base = result.path("ae_item_base_info_dto");
            String id = required(base, "product_id", 150, diagnostic);
            if (!id.equals(requestedId)) throw diagnostic.fail("product_id_mismatch");
            String name = required(base, "subject", 1000, diagnostic);
            String status = text(base, "product_status_type", diagnostic);
            if (status != null && !status.equalsIgnoreCase("onSelling")) throw new AliExpressIntegrationException(UNAVAILABLE);
            diagnostic.stage = "description";
            String rawDescription = Optional.ofNullable(text(base, "detail", diagnostic)).orElse("");
            if (rawDescription.length() > 200_000) throw diagnostic.fail("description_too_long");
            var document = Jsoup.parse(rawDescription);
            document.select("script,style,iframe,object,embed").remove();
            String description = document.text();
            List<String> warnings = new ArrayList<>();
            if (name.length() > 150) warnings.add("Titulo resumido no catalogo; titulo completo preservado nos metadados.");
            if (description.length() > 5000) warnings.add("Descricao resumida no catalogo; texto completo preservado nos metadados do fornecedor.");
            diagnostic.stage = "main_images";
            List<String> images = images(text(result.path("ae_multimedia_info_dto"), "image_urls", diagnostic), warnings, diagnostic);
            List<AliExpressProductPreview.Variant> variants = new ArrayList<>();
            Set<String> skuIds = new HashSet<>();
            diagnostic.stage = "variants";
            diagnostic.field = "ae_item_sku_info_dtos";
            for (JsonNode sku : array(result.path("ae_item_sku_info_dtos"), "ae_item_sku_info_d_t_o", diagnostic)) {
                diagnostic.stage = "variant";
                diagnostic.variantIndex++;
                diagnostic.propertyIndex = -1;
                String skuId = required(sku, "sku_id", 150, diagnostic);
                if (!skuIds.add(skuId)) throw diagnostic.fail("duplicate_sku_id");
                String raw = text(sku, "sku_attr", diagnostic);
                if (raw != null && raw.length() > 500) throw diagnostic.fail("sku_attr_too_long");
                Map<String,String> attrs = new TreeMap<>();
                List<String> variantImages = new ArrayList<>();
                // Keep property IDs as keys. Labels and raw sku_attr may disagree; never derive IDs from labels.
                diagnostic.stage = "variant_properties";
                diagnostic.field = "ae_sku_property_dtos";
                for (JsonNode prop : array(sku.path("ae_sku_property_dtos"), "ae_sku_property_d_t_o", diagnostic)) {
                    diagnostic.propertyIndex++;
                    String propertyId = text(prop, "sku_property_id", diagnostic);
                    String valueId = text(prop, "property_value_id", diagnostic);
                    String key = propertyId == null ? text(prop, "sku_property_name", diagnostic) : propertyId;
                    String label = text(prop, "property_value_definition_name", diagnostic);
                    if (label == null) label = text(prop, "sku_property_value", diagnostic);
                    if (key != null && label != null) {
                        put(attrs, key, label, diagnostic);
                        if (valueId != null) { diagnostic.field = "property_value_id"; put(attrs, key + ":valueId", valueId, diagnostic); }
                        String propertyName = text(prop, "sku_property_name", diagnostic);
                        if (propertyName != null) put(attrs, key + ":name", propertyName, diagnostic);
                        if (raw != null && propertyId != null && valueId != null
                                && !raw.matches("(?s).*(?:^|;)" + java.util.regex.Pattern.quote(propertyId + ":" + valueId) + "(?:#|;|$).*")) {
                            warnings.add("Atributos do SKU " + skuId + " inconsistentes; identificadores originais preservados.");
                        }
                    }
                    variantImages.addAll(images(text(prop,"sku_image", diagnostic), warnings, diagnostic));
                }
                diagnostic.propertyIndex = -1;
                diagnostic.stage = "variant_prices";
                BigDecimal regular = money(text(sku, "sku_price", diagnostic), diagnostic);
                String offer = text(sku, "offer_sale_price", diagnostic);
                BigDecimal price = offer == null ? regular : money(offer, diagnostic);
                diagnostic.stage = "variant_currency";
                String currency = required(sku, "currency_code", 3, diagnostic).toUpperCase(Locale.ROOT);
                java.util.Currency.getInstance(currency);
                diagnostic.stage = "variant_stock";
                String stockValue = required(sku, "sku_available_stock", 10, diagnostic);
                int stock = Integer.parseInt(stockValue);
                if (stock < 0) throw diagnostic.fail("negative_stock");
                diagnostic.stage = "variant";
                variants.add(new AliExpressProductPreview.Variant(skuId, bounded(text(sku,"sku_code", diagnostic),150, diagnostic), raw,
                        Collections.unmodifiableMap(attrs), variantImages.stream().distinct().toList(), price, regular, currency, stock));
            }
            diagnostic.stage = "variants";
            diagnostic.field = "ae_item_sku_info_dtos";
            diagnostic.variantIndex = -1;
            diagnostic.actualType = "ARRAY";
            if (variants.isEmpty() || variants.size() > 1000) throw diagnostic.fail(variants.isEmpty() ? "no_variants" : "too_many_variants");
            String currency = variants.get(0).currency();
            diagnostic.field = "currency_code";
            diagnostic.actualType = "NOT_INSPECTED";
            if (variants.stream().anyMatch(v -> !currency.equals(v.currency()))) throw diagnostic.fail("mixed_currencies");
            Map<String,String> properties = new TreeMap<>();
            diagnostic.stage = "product_attributes";
            diagnostic.field = "ae_item_properties";
            List<JsonNode> productAttributes = array(result.path("ae_item_properties"), "ae_item_property", diagnostic);
            Map<String,Integer> idCounts = new HashMap<>(), nameCounts = new HashMap<>();
            for (JsonNode attr : productAttributes) {
                diagnostic.propertyIndex++;
                String attributeId = bounded(text(attr,"attr_name_id", diagnostic),150,diagnostic);
                String attributeName = bounded(text(attr,"attr_name", diagnostic),150,diagnostic);
                if (attributeId != null) idCounts.merge(attributeId,1,Integer::sum);
                if (attributeName != null) nameCounts.merge(attributeName,1,Integer::sum);
            }
            diagnostic.propertyIndex = -1;
            for (JsonNode attr : productAttributes) {
                diagnostic.propertyIndex++;
                String attributeId = text(attr,"attr_name_id", diagnostic);
                String attributeName = text(attr,"attr_name", diagnostic);
                String value = text(attr,"attr_value", diagnostic);
                if (value == null) throw diagnostic.fail("required_value_missing_or_blank");
                // Product property IDs are not SKU IDs: -1 is a common provider sentinel.
                // Namespaces cannot overlap: numeric IDs, name:, property:, and source:.
                // Repeated names use source position so even conflicting values survive.
                String key = attributeId != null && attributeId.matches("[1-9][0-9]*") && idCounts.get(attributeId) == 1
                        ? attributeId
                        : attributeName != null && attributeName.length() <= 145 && nameCounts.get(attributeName) == 1
                            ? "name:" + attributeName : "property:" + diagnostic.propertyIndex;
                put(properties,key,value,diagnostic);
                String sourceKey = "source:" + diagnostic.propertyIndex + ":";
                put(properties,sourceKey + "key",key,diagnostic);
                if (attributeId != null) put(properties,sourceKey + "attr_name_id",attributeId,diagnostic);
                if (attributeName != null) put(properties,sourceKey + "attr_name",attributeName,diagnostic);
                String valueId = text(attr,"attr_value_id",diagnostic);
                if (valueId != null) put(properties,sourceKey + "attr_value_id",valueId,diagnostic);
            }
            diagnostic.propertyIndex = -1;
            diagnostic.stage = "package_info";
            Map<String,String> pack = fields(result.path("package_info_dto"), diagnostic, "gross_weight", "package_length", "package_width", "package_height", "base_unit", "package_type", "product_unit");
            diagnostic.stage = "logistics";
            Map<String,String> logistics = fields(result.path("logistics_info_dto"), diagnostic, "ship_to_country", "delivery_time");
            diagnostic.field = "ship_to_country";
            if (logistics.containsKey("ship_to_country") && !country.equalsIgnoreCase(logistics.get("ship_to_country"))) throw diagnostic.fail("country_mismatch");
            diagnostic.stage = "store";
            Map<String,String> store = fields(result.path("ae_store_info"), diagnostic, "store_id", "store_name", "store_country_code");
            warnings.add("Categoria comercial Nacionais e curadoria administrativa; envio nacional nao confirmado.");
            if (!pack.isEmpty()) warnings.add("Peso e dimensoes preservados como informados; unidades nao inferidas.");
            var preview = new AliExpressProductPreview(id,name,description,"ALIEXPRESS",currency,images, List.copyOf(variants),
                    Collections.unmodifiableMap(properties),pack,logistics,store,country,"Nacionais",false,warnings.stream().distinct().toList(),null);
            diagnostic.stage = "fingerprint";
            diagnostic.field = "normalized_preview";
            diagnostic.actualType = "NOT_INSPECTED";
            String fingerprint = AliExpressOAuthStateService.hash(JSON.writeValueAsString(preview));
            return new AliExpressProductPreview(id,name,description,"ALIEXPRESS",currency,images,preview.variants(),preview.attributes(),
                    pack,logistics,store,country,"Nacionais",false,preview.warnings(),fingerprint);
        } catch (AliExpressIntegrationException ex) { throw ex; }
        catch (Exception exception) { throw diagnostic.fail("unexpected_exception", exception); }
    }
    static List<JsonNode> array(JsonNode node, String wrapper) {
        return array(node, wrapper, new Diagnostic("array", "collection"));
    }
    private static List<JsonNode> array(JsonNode node, String wrapper, Diagnostic diagnostic) {
        if (node.isMissingNode() || node.isNull()) return List.of();
        if (!node.isArray()) node = node.path(wrapper);
        if (node.isMissingNode() || node.isNull()) return List.of();
        if (!node.isArray()) { diagnostic.actualType = node.getNodeType().name(); throw diagnostic.fail("expected_array"); }
        List<JsonNode> values = new ArrayList<>(); node.forEach(values::add); return values;
    }
    static List<String> images(String values, List<String> warnings) {
        return images(values, warnings, new Diagnostic("images", "image_urls"));
    }
    private static List<String> images(String values, List<String> warnings, Diagnostic diagnostic) {
        if (values == null) return List.of();
        Set<String> images = new LinkedHashSet<>();
        for (String value : values.split(";")) {
            value = value.trim();
            if (value.startsWith("//")) value = "https:" + value;
            try {
                URI uri = URI.create(value);
                String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
                boolean allowed = host.equals("alicdn.com") || host.endsWith(".alicdn.com")
                        || host.equals("aliexpress-media.com") || host.endsWith(".aliexpress-media.com");
                if (!allowed || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                        || uri.getRawUserInfo() != null || uri.getPort() != -1 || uri.getRawFragment() != null || value.length() > 2000) throw new IllegalArgumentException();
                // AliExpress CDN supports HTTPS; never fetch these URLs in the backend.
                images.add("https" + value.substring(value.indexOf(':')));
            } catch (IllegalArgumentException ignored) { warnings.add("Imagem invalida ou fora dos CDNs permitidos foi omitida."); }
        }
        if (images.size() > 100) throw diagnostic.fail("too_many_images");
        return List.copyOf(images);
    }
    static BigDecimal money(String value) {
        return money(value, new Diagnostic("price", "amount"));
    }
    private static BigDecimal money(String value, Diagnostic diagnostic) {
        if (value == null || !value.matches("[0-9]{1,15}(?:\\.[0-9]{1,4})?")) throw diagnostic.fail("invalid_price_format");
        BigDecimal money = new BigDecimal(value);
        if (money.signum() <= 0) throw diagnostic.fail("non_positive_price");
        return money;
    }
    private static Map<String,String> fields(JsonNode node, Diagnostic diagnostic, String... names) {
        Map<String,String> fields = new TreeMap<>();
        for (String name : names) { String value = text(node,name,diagnostic); if (value != null) put(fields,name,value,diagnostic); }
        return Collections.unmodifiableMap(fields);
    }
    private static void put(Map<String,String> map, String key, String value, Diagnostic diagnostic) {
        if (key.length() > 150) { diagnostic.field = "attribute_key"; throw diagnostic.fail("attribute_key_too_long"); }
        if (value.length() > 1000) throw diagnostic.fail("attribute_value_too_long");
        if (map.containsKey(key) && !map.get(key).equals(value)) throw diagnostic.fail("conflicting_attribute");
        map.put(key, value);
    }
    private static String text(JsonNode node, String field, Diagnostic diagnostic) {
        diagnostic.field = field;
        JsonNode value = node.path(field);
        diagnostic.actualType = value.getNodeType().name();
        if (value.isMissingNode() || value.isNull()) return null;
        if (!value.isValueNode()) throw diagnostic.fail("expected_scalar");
        String text = value.asText().trim(); return text.isEmpty() ? null : text;
    }
    private static String required(JsonNode node, String field, int length, Diagnostic diagnostic) {
        String value = bounded(text(node,field,diagnostic),length,diagnostic);
        if (value == null) throw diagnostic.fail("required_value_missing_or_blank"); return value;
    }
    private static String bounded(String value, int length, Diagnostic diagnostic) {
        if (value != null && value.length() > length) throw diagnostic.fail("value_too_long"); return value;
    }
    private static final class Diagnostic {
        private String stage, field;
        private String actualType = "NOT_INSPECTED";
        private int variantIndex = -1, propertyIndex = -1;
        private Diagnostic(String stage, String field) { this.stage = stage; this.field = field; }
        private AliExpressIntegrationException fail(String condition) { return fail(condition, null); }
        private AliExpressIntegrationException fail(String condition, Exception exception) {
            // Only fixed stage/field/condition names, indices and types. Never log source values,
            // dynamic attribute keys, exception messages/causes, URLs or response bodies.
            LOG.warn("AliExpress preview incomplete; stage={}; field={}; condition={}; variantIndex={}; propertyIndex={}; actualType={}; exceptionType={}",
                    stage, field, condition, variantIndex, propertyIndex, actualType,
                    exception == null ? "NONE" : exception.getClass().getSimpleName());
            return new AliExpressIntegrationException(INCOMPLETE);
        }
    }
}
