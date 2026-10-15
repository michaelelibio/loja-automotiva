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
    public AliExpressProductPreview normalize(JsonNode result, String requestedId, String country) {
        try {
            JsonNode base = result.path("ae_item_base_info_dto");
            String id = required(base, "product_id", 150);
            if (!id.equals(requestedId)) throw new AliExpressIntegrationException(INCOMPLETE);
            String name = required(base, "subject", 1000);
            String status = text(base, "product_status_type");
            if (status != null && !status.equalsIgnoreCase("onSelling")) throw new AliExpressIntegrationException(UNAVAILABLE);
            String rawDescription = Optional.ofNullable(text(base, "detail")).orElse("");
            if (rawDescription.length() > 200_000) throw new AliExpressIntegrationException(INCOMPLETE);
            var document = Jsoup.parse(rawDescription);
            document.select("script,style,iframe,object,embed").remove();
            String description = document.text();
            List<String> warnings = new ArrayList<>();
            if (name.length() > 150) warnings.add("Titulo resumido no catalogo; titulo completo preservado nos metadados.");
            if (description.length() > 5000) warnings.add("Descricao resumida no catalogo; texto completo preservado nos metadados do fornecedor.");
            List<String> images = images(text(result.path("ae_multimedia_info_dto"), "image_urls"), warnings);
            List<AliExpressProductPreview.Variant> variants = new ArrayList<>();
            Set<String> skuIds = new HashSet<>();
            for (JsonNode sku : array(result.path("ae_item_sku_info_dtos"), "ae_item_sku_info_d_t_o")) {
                String skuId = required(sku, "sku_id", 150);
                if (!skuIds.add(skuId)) throw new AliExpressIntegrationException(INCOMPLETE);
                String raw = text(sku, "sku_attr");
                if (raw != null && raw.length() > 500) throw new AliExpressIntegrationException(INCOMPLETE);
                Map<String,String> attrs = new TreeMap<>();
                List<String> variantImages = new ArrayList<>();
                // Keep property IDs as keys. Labels and raw sku_attr may disagree; never derive IDs from labels.
                for (JsonNode prop : array(sku.path("ae_sku_property_dtos"), "ae_sku_property_d_t_o")) {
                    String propertyId = text(prop, "sku_property_id");
                    String valueId = text(prop, "property_value_id");
                    String key = propertyId == null ? text(prop, "sku_property_name") : propertyId;
                    String label = text(prop, "property_value_definition_name");
                    if (label == null) label = text(prop, "sku_property_value");
                    if (key != null && label != null) {
                        put(attrs, key, label);
                        if (valueId != null) put(attrs, key + ":valueId", valueId);
                        String propertyName = text(prop, "sku_property_name");
                        if (propertyName != null) put(attrs, key + ":name", propertyName);
                        if (raw != null && propertyId != null && valueId != null
                                && !raw.matches("(?s).*(?:^|;)" + java.util.regex.Pattern.quote(propertyId + ":" + valueId) + "(?:#|;|$).*")) {
                            warnings.add("Atributos do SKU " + skuId + " inconsistentes; identificadores originais preservados.");
                        }
                    }
                    variantImages.addAll(images(text(prop,"sku_image"), warnings));
                }
                BigDecimal regular = money(text(sku, "sku_price"));
                String offer = text(sku, "offer_sale_price");
                BigDecimal price = offer == null ? regular : money(offer);
                String currency = required(sku, "currency_code", 3).toUpperCase(Locale.ROOT);
                java.util.Currency.getInstance(currency);
                String stockValue = required(sku, "sku_available_stock", 10);
                int stock = Integer.parseInt(stockValue);
                if (stock < 0) throw new AliExpressIntegrationException(INCOMPLETE);
                variants.add(new AliExpressProductPreview.Variant(skuId, bounded(text(sku,"sku_code"),150), raw,
                        Collections.unmodifiableMap(attrs), variantImages.stream().distinct().toList(), price, regular, currency, stock));
            }
            if (variants.isEmpty() || variants.size() > 1000) throw new AliExpressIntegrationException(INCOMPLETE);
            String currency = variants.get(0).currency();
            if (variants.stream().anyMatch(v -> !currency.equals(v.currency()))) throw new AliExpressIntegrationException(INCOMPLETE);
            Map<String,String> properties = new TreeMap<>();
            for (JsonNode attr : array(result.path("ae_item_properties"), "ae_item_property")) {
                String key = text(attr,"attr_name_id");
                if (key == null) key = text(attr,"attr_name");
                String value = text(attr,"attr_value");
                if (key != null && value != null) put(properties,key,value);
            }
            Map<String,String> pack = fields(result.path("package_info_dto"), "gross_weight", "package_length", "package_width", "package_height", "base_unit", "package_type", "product_unit");
            Map<String,String> logistics = fields(result.path("logistics_info_dto"), "ship_to_country", "delivery_time");
            if (logistics.containsKey("ship_to_country") && !country.equalsIgnoreCase(logistics.get("ship_to_country"))) throw new AliExpressIntegrationException(INCOMPLETE);
            Map<String,String> store = fields(result.path("ae_store_info"), "store_id", "store_name", "store_country_code");
            warnings.add("Categoria comercial Nacionais e curadoria administrativa; envio nacional nao confirmado.");
            if (!pack.isEmpty()) warnings.add("Peso e dimensoes preservados como informados; unidades nao inferidas.");
            var preview = new AliExpressProductPreview(id,name,description,"ALIEXPRESS",currency,images, List.copyOf(variants),
                    Collections.unmodifiableMap(properties),pack,logistics,store,country,"Nacionais",false,warnings.stream().distinct().toList(),null);
            String fingerprint = AliExpressOAuthStateService.hash(JSON.writeValueAsString(preview));
            return new AliExpressProductPreview(id,name,description,"ALIEXPRESS",currency,images,preview.variants(),preview.attributes(),
                    pack,logistics,store,country,"Nacionais",false,preview.warnings(),fingerprint);
        } catch (AliExpressIntegrationException ex) { throw ex; }
        catch (Exception ignored) { throw new AliExpressIntegrationException(INCOMPLETE); }
    }
    static List<JsonNode> array(JsonNode node, String wrapper) {
        if (node.isMissingNode() || node.isNull()) return List.of();
        if (!node.isArray()) node = node.path(wrapper);
        if (node.isMissingNode() || node.isNull()) return List.of();
        if (!node.isArray()) throw new AliExpressIntegrationException(INCOMPLETE);
        List<JsonNode> values = new ArrayList<>(); node.forEach(values::add); return values;
    }
    static List<String> images(String values, List<String> warnings) {
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
        if (images.size() > 100) throw new AliExpressIntegrationException(INCOMPLETE);
        return List.copyOf(images);
    }
    static BigDecimal money(String value) {
        if (value == null || !value.matches("[0-9]{1,15}(?:\\.[0-9]{1,4})?")) throw new AliExpressIntegrationException(INCOMPLETE);
        BigDecimal money = new BigDecimal(value);
        if (money.signum() <= 0) throw new AliExpressIntegrationException(INCOMPLETE);
        return money;
    }
    private static Map<String,String> fields(JsonNode node, String... names) {
        Map<String,String> fields = new TreeMap<>();
        for (String name : names) { String value = text(node,name); if (value != null) put(fields,name,value); }
        return Collections.unmodifiableMap(fields);
    }
    private static void put(Map<String,String> map, String key, String value) {
        if (key.length() > 150 || value.length() > 1000 || (map.containsKey(key) && !map.get(key).equals(value))) throw new AliExpressIntegrationException(INCOMPLETE);
        map.put(key, value);
    }
    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        if (!value.isValueNode()) throw new AliExpressIntegrationException(INCOMPLETE);
        String text = value.asText().trim(); return text.isEmpty() ? null : text;
    }
    private static String required(JsonNode node, String field, int length) {
        String value = bounded(text(node,field),length);
        if (value == null) throw new AliExpressIntegrationException(INCOMPLETE); return value;
    }
    private static String bounded(String value, int length) {
        if (value != null && value.length() > length) throw new AliExpressIntegrationException(INCOMPLETE); return value;
    }
}
