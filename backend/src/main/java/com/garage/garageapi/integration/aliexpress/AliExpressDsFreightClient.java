package com.garage.garageapi.integration.aliexpress;

import org.springframework.beans.factory.annotation.*;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.*;

import java.util.*;
import static com.garage.garageapi.integration.aliexpress.AliExpressFreightDiagnosticResult.*;
import static com.garage.garageapi.integration.aliexpress.AliExpressFreightDiagnosticResult.Status.*;

/** Shared read-only ds.freight.query transport and parser for ADM and checkout probing. */
@Component
public class AliExpressDsFreightClient {
    private static final JsonMapper JSON = JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    private static final int MAX_RESPONSE = 262144;
    private final RestClient client;
    private final String appKey, secret;
    private final Clock clock;

    @Autowired
    public AliExpressDsFreightClient(@Value("${aliexpress.app-key:}") String appKey,
            @Value("${aliexpress.app-secret:}") String secret) {
        this(defaultClient(),appKey,secret,Clock.systemUTC());
    }
    AliExpressDsFreightClient(RestClient client,String appKey,String secret,Clock clock) {
        this.client=client;this.appKey=appKey;this.secret=secret;this.clock=clock;
    }

    public record Input(String productId, String skuId, int quantity, String destinationCountry, String province) {
        public Input(String productId,String skuId,int quantity,String country) { this(productId,skuId,quantity,country,null); }
    }
    public record RequestEvidence(List<String> queryDeliveryReqFields, String shipToCountry, String province,
            boolean requestBodyWritten, boolean httpResponseReceived) {
        public RequestEvidence { queryDeliveryReqFields=List.copyOf(queryDeliveryReqFields); }
    }
    public record DeliveryEvidence(int optionIndex, String serviceCode, String shipFromCountry, String routeEvidence) {}
    public record Observation(RequestEvidence request, AliExpressFreightDiagnosticResult result, List<DeliveryEvidence> deliveryOptions) {
        public Observation { deliveryOptions=List.copyOf(deliveryOptions); }
        public Observation(RequestEvidence request,AliExpressFreightDiagnosticResult result) { this(request,result,List.of()); }
    }
    private static final class Probe {
        List<String> fields=List.of();String country,province;boolean written,received;
        List<DeliveryEvidence> delivery=List.of();
    }
    public Observation queryObserved(String token,Input input) {
        Probe probe=new Probe();
        var result=queryInternal(token,input,probe);
        if (probe.fields.contains("province")) result=new AliExpressFreightDiagnosticResult(result.status(),result.safeCode(),result.methods(),
                result.limitations().stream().map(limit->limit.equals("COUNTRY_ONLY_BR_DIAGNOSTIC")
                        ?"COUNTRY_AND_PROVINCE_DIAGNOSTIC_NOT_ADDRESS_CONFIRMED":limit).toList(),result.monetaryStructure());
        return new Observation(new RequestEvidence(probe.fields,probe.country,probe.province,probe.written,probe.received),result,probe.delivery);
    }

    public AliExpressFreightDiagnosticResult query(String token, Input input) {
        return queryInternal(token,input,null);
    }
    private AliExpressFreightDiagnosticResult queryInternal(String token, Input input, Probe probe) {
        if (input == null || input.productId() == null || !input.productId().matches("[0-9]{1,18}")
                || input.skuId() == null || !input.skuId().matches("[0-9]{1,30}")
                || input.quantity() < 1 || input.quantity() > 100
                || !"BR".equals(input.destinationCountry())
                || (input.province()!=null && !"Santa Catarina".equals(input.province())))
            return failure(PARAMETER_ERROR,"INVALID_TEST_INPUT");
        if (appKey.isBlank() || secret.isBlank() || appKey.contains("COLE_AQUI") || secret.contains("COLE_AQUI"))
            return failure(NOT_CONFIGURED,"APPLICATION_NOT_CONFIGURED");
        if (token == null || token.isBlank()) return failure(LOCAL_AUTHORIZATION_UNAVAILABLE,"LOCAL_TOKEN_UNAVAILABLE");
        try {
            Map<String,Object> delivery=new TreeMap<>(Map.of("productId",input.productId(),
                    "quantity",input.quantity(),"shipToCountry",input.destinationCountry(), "selectedSkuId",input.skuId(),
                    "language","pt_BR","locale","pt_BR","currency","BRL"));
            if (input.province()!=null) delivery.put("province",input.province());
            String dto=JSON.writeValueAsString(delivery);
            if (probe!=null) {
                probe.fields=List.copyOf(delivery.keySet());probe.country=(String)delivery.get("shipToCountry");probe.province=(String)delivery.get("province");
            }
            Map<String,String> params = new TreeMap<>();
            params.put("method","aliexpress.ds.freight.query");params.put("app_key",appKey);
            params.put("session",token);params.put("sign_method","sha256");params.put("format","json");params.put("v","2.0");
            params.put("timestamp",Long.toString(clock.millis()));
            params.put("simplify","true");
            params.put("queryDeliveryReq",dto);
            StringBuilder canonical = new StringBuilder();params.forEach((key,value)->canonical.append(key).append(value));
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            params.put("sign",HexFormat.of().withUpperCase().formatHex(mac.doFinal(canonical.toString().getBytes(StandardCharsets.UTF_8))));
            StringJoiner form=new StringJoiner("&");
            params.forEach((key,value)->form.add(URLEncoder.encode(key,StandardCharsets.UTF_8)+"="+URLEncoder.encode(value,StandardCharsets.UTF_8)));
            byte[] body=form.toString().getBytes(StandardCharsets.UTF_8);
            // Streaming avoids form converter logging. Exchange avoids raw response converter logging.
            // No redirects, retries, fallback protocols, OAuth refresh or additional product calls.
            return client.post().uri("/sync").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON).body(output->{ output.write(body);if(probe!=null) probe.written=true; }).exchange((request,response)-> {
                        if (probe!=null) probe.received=true;
                        int status=response.getStatusCode().value();
                        if (status==429) return failure(RATE_LIMITED,"HTTP_RATE_LIMIT");
                        if (status<200 || status>=300) return failure(COMMUNICATION_ERROR,"HTTP_FAILURE");
                        byte[] bytes=response.getBody().readNBytes(MAX_RESPONSE+1);
                        if (bytes.length>MAX_RESPONSE) return failure(INVALID_RESPONSE,"RESPONSE_TOO_LARGE");
                        final JsonNode parsed;
                        try { parsed=JSON.readTree(bytes); }
                        catch (Exception ignored) { return failure(INVALID_RESPONSE,"INVALID_JSON_RESPONSE"); }
                        if(probe!=null) probe.delivery=deliveryEvidence(parsed,token);
                        return parse(parsed,token).withMonetaryStructure(monetaryStructure(parsed,token));
                    });
        } catch (Exception ignored) {
            return failure(COMMUNICATION_ERROR,"COMMUNICATION_FAILURE");
        }
    }

    /** Independent metadata projection: a monetary rejection must not hide an observed shipping origin.
     * A same-country route is only a local-to-local candidate, not program membership or warehouse identity. */
    private List<DeliveryEvidence> deliveryEvidence(JsonNode body,String token) {
        JsonNode result=body.path("aliexpress_ds_freight_query_response").path("result");
        if(result.isMissingNode()) result=body.path("result");
        JsonNode entries=result.path("delivery_options");
        if(!"true".equals(result.path("success").asText()) || !"200".equals(result.path("code").asText())
                || !entries.isArray() || entries.size()>500) return List.of();
        List<DeliveryEvidence> evidence=new ArrayList<>();
        for(int i=0;i<entries.size();i++) {
            JsonNode entry=entries.get(i);
            String service=entry.path("code").isString()?entry.path("code").asText():null;
            if(service==null || !service.matches("[A-Za-z0-9_.-]{1,120}") || sensitive(service,token)) service=null;
            String origin=entry.path("ship_from_country").isString()?entry.path("ship_from_country").asText():null;
            if(origin==null || !Arrays.asList(Locale.getISOCountries()).contains(origin) || sensitive(origin,token)) origin=null;
            String route=origin==null?"ORIGIN_NOT_CONFIRMED":origin.equals("BR")?"SAME_COUNTRY_ROUTE_CANDIDATE":"CROSS_BORDER_ROUTE_OBSERVED";
            evidence.add(new DeliveryEvidence(i,service,origin,route));
        }
        return evidence;
    }

    private AliExpressFreightDiagnosticResult parse(JsonNode body,String token) {
        if (body==null || !body.isObject()) return failure(INVALID_RESPONSE,"INVALID_RESPONSE_SHAPE");
        JsonNode error=body.path("error_response");
        if (error.isObject()) return rejection(error,true);
        JsonNode result=body.path("aliexpress_ds_freight_query_response").path("result");
        if (result.isMissingNode()) result=body.path("result");
        if (!result.isObject() || !isBoolean(result.path("success"))) return failure(INVALID_RESPONSE,"INVALID_RESPONSE_SHAPE");
        if (!"true".equals(result.path("success").asText())) return rejection(result,false);
        if (!"200".equals(result.path("code").asText())) return failure(INVALID_RESPONSE,"CONTRADICTORY_SUCCESS_CODE");
        JsonNode entries=result.path("delivery_options");
        if (!entries.isArray() || entries.size()>500) return failure(INVALID_RESPONSE,"INVALID_METHOD_LIST");
        if (entries.isEmpty()) return failure(PRODUCT_UNAVAILABLE,"NO_DELIVERY_METHODS");
        List<Method> methods=new ArrayList<>();
        for (JsonNode entry:entries) {
            // No DS scale is proven for these aliases; never silently ignore a second monetary source.
            if (List.of("shipping_fee","amount","cent","currency_code").stream().anyMatch(entry::has))
                return failure(INVALID_RESPONSE,"CONFLICTING_MONETARY_FIELDS");
            String cent=entry.path("shipping_fee_cent").asText();
            // docId 1579 says cents, but its example contains "1.99" next to
            // "US $1.99". Decimal values are ambiguous: never divide or guess.
            if (cent.matches("[0-9]{1,16}\\.[0-9]+")) return failure(INVALID_RESPONSE,"AMBIGUOUS_MONETARY_UNIT");
            String currency=entry.path("shipping_fee_currency").asText(),service=entry.path("code").asText();
            if (!service.matches("[A-Za-z0-9_.-]{1,120}") || sensitive(service,token))
                return failure(INVALID_RESPONSE,"INVALID_DELIVERY_METHOD");
            if (!cent.matches("[0-9]{1,16}") || !currency.matches("[A-Z]{3}"))
                return failure(INVALID_RESPONSE,"INVALID_METHOD_DATA");
            // The contract explicitly defines this field in cents. Never parse formatted fees.
            BigDecimal value=new BigDecimal(cent).movePointLeft(2);
            // Formatted text is never the source of an amount. If its simple
            // decimal representation contradicts cents, report the conflict.
            var formatted=java.util.regex.Pattern.compile("^(?:[A-Z]{1,3}\\s*)?[$€£]?\\s*([0-9]{1,16}\\.[0-9]{2})$")
                    .matcher(entry.path("shipping_fee_format").asText());
            if (formatted.matches() && value.compareTo(new BigDecimal(formatted.group(1)))!=0)
                return failure(INVALID_RESPONSE,"AMBIGUOUS_MONETARY_UNIT");
            JsonNode free=entry.path("free_shipping");
            if (!isBoolean(free) || ("true".equals(free.asText()) != (value.signum()==0)))
                return failure(INVALID_RESPONSE,"CONFLICTING_MONETARY_FIELDS");
            String origin=entry.path("ship_from_country").asText();
            if (!Arrays.asList(Locale.getISOCountries()).contains(origin)) origin=null;
            String company=entry.path("company").asText();
            if (!company.matches("[A-Za-z][A-Za-z0-9 ._()/ -]{0,119}") || company.matches(".*[0-9]{8,}.*") || sensitive(company,token)) company=null;
            String estimate=estimate(entry,token);
            methods.add(new Method(service,value,currency,estimate,company,origin));
        }
        if (methods.stream().map(Method::serviceName).distinct().count()!=methods.size()) return failure(INVALID_RESPONSE,"DUPLICATE_SERVICE");
        return result(SUCCESS,"CALL_SUCCEEDED",methods);
    }

    /** Fixed allowlist: no arbitrary names, raw objects, formatted text or personal data copied. */
    private List<MoneyField> monetaryStructure(JsonNode body,String token) {
        JsonNode result=body.path("aliexpress_ds_freight_query_response").path("result");
        if (result.isMissingNode()) result=body.path("result");
        JsonNode entries=result.path("delivery_options");
        if (!entries.isArray() || entries.size()>500) return List.of();
        List<MoneyField> fields=new ArrayList<>();
        for (int i=0;i<entries.size();i++) {
            JsonNode entry=entries.get(i);
            String currency=entry.path("shipping_fee_currency").asText();
            if (!Set.of("BRL","USD","EUR","GBP","JPY","CNY","CAD","AUD").contains(currency)
                    || sensitive(currency,token)) currency=null;
            for (String name:List.of("shipping_fee_cent","shipping_fee_currency","shipping_fee_format",
                    "free_shipping","shipping_fee","amount","cent","currency_code","free_shipping_threshold")) {
                addMoneyField(fields,i,name,entry.path(name),currency,token);
                if (name.equals("shipping_fee_format")) {
                    MoneyField field=fields.remove(fields.size()-1);
                    String comparison="UNAVAILABLE";
                    String cent=entry.path("shipping_fee_cent").asText();
                    if (currency!=null && field.safeValue()!=null && cent.matches("[0-9]{1,6}(?:\\.[0-9]{1,4})?") && !sensitive(cent,token)) {
                        BigDecimal observed=new BigDecimal(cent), display=new BigDecimal(field.safeValue());
                        boolean major=observed.compareTo(display)==0, minor=observed.movePointLeft(2).compareTo(display)==0;
                        comparison=major && minor?"BOTH_MATCH":major?"MAJOR_MATCH":minor?"MINOR_MATCH":"NEITHER_MATCH";
                    }
                    fields.add(new MoneyField(i,name,field.present(),field.jsonType(),currency,field.documentedScale(),field.safeValue(),comparison));
                }
                if (Set.of("shipping_fee","amount").contains(name) && entry.path(name).isObject())
                    for (String child:List.of("amount","cent","currency_code"))
                        addMoneyField(fields,i,name+"."+child,entry.path(name).path(child),currency,token);
            }
        }
        return fields;
    }
    private void addMoneyField(List<MoneyField> fields,int index,String path,JsonNode node,String currency,String token) {
        String type=node.isMissingNode()?"MISSING":node.isNull()?"NULL":node.isString()?"STRING":
                node.isNumber()?"NUMBER":node.isBoolean()?"BOOLEAN":node.isArray()?"ARRAY":"OBJECT";
        String scale=path.equals("shipping_fee_cent")?"CENT_1_100_DESCRIPTION_EXAMPLE_CONFLICT":
                path.equals("shipping_fee_format")?"DISPLAY_TEXT_ONLY":"NOT_DOCUMENTED_AS_AMOUNT";
        String value=null;
        // Only bounded numeric freight scalars. Long digit sequences and all free text are suppressed.
        if (Set.of("shipping_fee_cent","shipping_fee","amount","cent").contains(path)
                && (node.isString() || node.isNumber()) && node.asText().matches("[0-9]{1,6}(?:\\.[0-9]{1,4})?")
                && !sensitive(node.asText(),token)) value=new BigDecimal(node.asText()).toPlainString();
        if (path.equals("free_shipping") && isBoolean(node)) value=node.asText();
        if (path.equals("shipping_fee_format") && node.isString() && !sensitive(node.asText(),token)) {
            var formatted=java.util.regex.Pattern.compile("^(?:[A-Z]{1,3}\\s*)?[$€£]?\\s*([0-9]{1,6}\\.[0-9]{2})$")
                    .matcher(node.asText());
            if (formatted.matches() && currency!=null && (node.asText().matches("[0-9]{1,6}\\.[0-9]{2}")
                    || node.asText().startsWith(currency+" ")
                    || ("USD".equals(currency) && node.asText().matches("(?:US )?\\$[0-9]{1,6}\\.[0-9]{2}"))))
                value=new BigDecimal(formatted.group(1)).toPlainString();
            // BRL local presentation only: anchored, bounded, no grouping or free text.
            // Never used to select a checkout scale or populate Method.amount.
            if ("BRL".equals(currency)) {
                var brl=java.util.regex.Pattern.compile("^(?:R\\$|BRL)[ \\u00a0]?([0-9]{1,6})([.,])([0-9]{2})$").matcher(node.asText());
                if (brl.matches()) value=new BigDecimal(brl.group(1)+"."+brl.group(3)).toPlainString();
            }
        }
        if ((path.equals("shipping_fee_currency") || path.endsWith("currency_code")) && node.isString()
                && Set.of("BRL","USD","EUR","GBP","JPY","CNY","CAD","AUD").contains(node.asText())
                && !sensitive(node.asText(),token)) value=node.asText();
        fields.add(new MoneyField(index,path,!node.isMissingNode(),type,currency,scale,value));
    }

    private static boolean isBoolean(JsonNode node) {
        return node.isBoolean() || (node.isString() && Set.of("true","false").contains(node.asText()));
    }
    private String estimate(JsonNode entry,String token) {
        String minimum=entry.path("min_delivery_days").asText(), maximum=entry.path("max_delivery_days").asText();
        if (minimum.matches("[0-9]{1,4}") && maximum.matches("[0-9]{1,4}")
                && Integer.parseInt(minimum)>0 && Integer.parseInt(maximum)>=Integer.parseInt(minimum))
            return minimum+"-"+maximum+"days";
        String estimate=entry.path("estimated_delivery_time").asText();
        return estimate.matches("[0-9]{1,4}(?:[-~][0-9]{1,4})?(?:days?|d)?") && !sensitive(estimate,token) ? estimate : null;
    }

    private boolean sensitive(String value,String token) { return value.contains(token) || value.contains(secret) || value.contains(appKey); }

    private AliExpressFreightDiagnosticResult rejection(JsonNode error,boolean platformError) {
        String code=error.path("code").asText();
        if (code.isEmpty()) code=error.path("error_code").asText();
        String sub=error.path("sub_code").asText();
        // TOP platform codes have meaning only inside error_response. Business
        // error_code uses a different namespace and must not be guessed as TOP.
        if (platformError) {
            String protocolCode=switch(code) {
                case "21" -> "TOP_21_MISSING_METHOD";
                case "22" -> "TOP_22_INVALID_METHOD";
                case "24" -> "TOP_24_MISSING_SIGNATURE";
                case "25" -> "TOP_25_INVALID_SIGNATURE";
                case "28" -> "TOP_28_MISSING_APP_KEY";
                case "29" -> "TOP_29_INVALID_APP_KEY";
                default -> null;
            };
            if (protocolCode!=null) return failure(PROTOCOL_ERROR,protocolCode);
        }
        if ((platformError && (code.equals("27") || code.equals("26"))) || Set.of("invalid-sessionkey","invalid-session","isv.invalid-session").contains(sub))
            return failure(TOKEN_REJECTED,"PROVIDER_TOKEN_REJECTED");
        if ((platformError && code.equals("11")) || Set.of("isv.permission-denied","isv.permission-api-package-empty","isv.permission-api-package-not-allowed","isv.permission-api-package-limit","isv.permission-ip-whitelist-limit").contains(sub))
            return failure(PERMISSION_DENIED,"PROVIDER_PERMISSION_DENIED");
        if (platformError && code.equals("7")) return failure(RATE_LIMITED,"PROVIDER_RATE_LIMIT");
        if (Set.of("isv.invalid-parameter","isv.missing-parameter").contains(sub)) return failure(PARAMETER_ERROR,"PROVIDER_PARAMETER_REJECTED");
        // Exact symbolic business errors published by docId 1579. Never infer
        // availability or permission from arbitrary free-form error text.
        for (String value:List.of(code,sub,error.path("msg").asText())) {
            if (value.equals("DELIVERY_NOT_AVAILABLE_TO_YOUR_ADDRESS"))
                return failure(PRODUCT_UNAVAILABLE,"PROVIDER_DELIVERY_UNAVAILABLE");
            if (value.equals("DELIVERY_INFO_EMPTY"))
                return failure(PARAMETER_ERROR,"PROVIDER_PRODUCT_ID_REJECTED");
        }
        // Do not guess permissions/product availability from free-form messages or unknown codes.
        return failure(PROVIDER_REJECTED,"UNCLASSIFIED_PROVIDER_REJECTION");
    }

    private static RestClient defaultClient() {
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build());factory.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder().baseUrl("https://api-sg.aliexpress.com").requestFactory(factory).build();
    }
}
