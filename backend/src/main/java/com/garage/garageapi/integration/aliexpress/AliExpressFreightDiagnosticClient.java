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
import java.time.format.DateTimeFormatter;
import java.util.*;
import static com.garage.garageapi.integration.aliexpress.AliExpressFreightDiagnosticResult.*;
import static com.garage.garageapi.integration.aliexpress.AliExpressFreightDiagnosticResult.Status.*;

/** Fixed, read-only diagnostic of the public TOP contract. Never used by checkout. */
@Component
public class AliExpressFreightDiagnosticClient {
    private static final JsonMapper JSON = JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    private static final int MAX_RESPONSE = 262144;
    private final RestClient client;
    private final String appKey, secret;
    private final Clock clock;

    @Autowired
    public AliExpressFreightDiagnosticClient(@Value("${aliexpress.app-key:}") String appKey,
            @Value("${aliexpress.app-secret:}") String secret) {
        this(defaultClient(),appKey,secret,Clock.systemUTC());
    }
    AliExpressFreightDiagnosticClient(RestClient client,String appKey,String secret,Clock clock) {
        this.client=client;this.appKey=appKey;this.secret=secret;this.clock=clock;
    }

    public AliExpressFreightDiagnosticResult query(String token) {
        if (appKey.isBlank() || secret.isBlank() || appKey.contains("COLE_AQUI") || secret.contains("COLE_AQUI"))
            return failure(NOT_CONFIGURED,"APPLICATION_NOT_CONFIGURED");
        if (token == null || token.isBlank()) return failure(LOCAL_AUTHORIZATION_UNAVAILABLE,"LOCAL_TOKEN_UNAVAILABLE");
        try {
            // sku_id is absent from this documented DTO; do not invent an extension.
            String dto = "{\"product_id\":1005010661577657,\"product_num\":1,\"country_code\":\"BR\","
                    + "\"send_goods_country_code\":\"CN\",\"price\":\"25.39\",\"price_currency\":\"BRL\"}";
            Map<String,String> params = new TreeMap<>();
            params.put("method","aliexpress.logistics.buyer.freight.calculate");params.put("app_key",appKey);
            params.put("session",token);params.put("sign_method","hmac");params.put("format","json");params.put("v","2.0");
            params.put("timestamp",DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.ofHours(8)).format(clock.instant()));
            params.put("param_aeop_freight_calculate_for_buyer_d_t_o",dto);
            StringBuilder input = new StringBuilder();params.forEach((key,value)->input.append(key).append(value));
            Mac mac=Mac.getInstance("HmacMD5");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacMD5"));
            params.put("sign",HexFormat.of().withUpperCase().formatHex(mac.doFinal(input.toString().getBytes(StandardCharsets.UTF_8))));
            StringJoiner form=new StringJoiner("&");
            params.forEach((key,value)->form.add(URLEncoder.encode(key,StandardCharsets.UTF_8)+"="+URLEncoder.encode(value,StandardCharsets.UTF_8)));
            byte[] body=form.toString().getBytes(StandardCharsets.UTF_8);
            // Streaming avoids form converter logging. Exchange avoids raw response converter logging.
            // No redirects, retries, fallback protocols, OAuth refresh or additional product calls.
            return client.post().uri("/router/rest").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON).body(output->output.write(body)).exchange((request,response)-> {
                        int status=response.getStatusCode().value();
                        if (status==429) return failure(RATE_LIMITED,"HTTP_RATE_LIMIT");
                        if (status<200 || status>=300) return failure(COMMUNICATION_ERROR,"HTTP_FAILURE");
                        byte[] bytes=response.getBody().readNBytes(MAX_RESPONSE+1);
                        if (bytes.length>MAX_RESPONSE) return failure(INVALID_RESPONSE,"RESPONSE_TOO_LARGE");
                        final JsonNode parsed;
                        try { parsed=JSON.readTree(bytes); }
                        catch (Exception ignored) { return failure(INVALID_RESPONSE,"INVALID_JSON_RESPONSE"); }
                        return parse(parsed,token);
                    });
        } catch (Exception ignored) {
            return failure(COMMUNICATION_ERROR,"COMMUNICATION_FAILURE");
        }
    }

    private AliExpressFreightDiagnosticResult parse(JsonNode body,String token) {
        if (body==null || !body.isObject()) return failure(INVALID_RESPONSE,"INVALID_RESPONSE_SHAPE");
        JsonNode error=body.path("error_response");
        if (error.isObject()) return rejection(error);
        JsonNode result=body.path("aliexpress_logistics_buyer_freight_calculate_response").path("result");
        if (!result.isObject() || !result.path("success").isBoolean()) return failure(INVALID_RESPONSE,"INVALID_RESPONSE_SHAPE");
        if (!result.path("success").asBoolean()) return rejection(result);
        JsonNode entries=result.path("aeop_freight_calculate_result_for_buyer_d_t_o_list").path("aeop_freight_calculate_result_for_buyer_dto");
        if (!entries.isArray()) return failure(INVALID_RESPONSE,"INVALID_METHOD_LIST");
        if (entries.isEmpty()) return failure(PRODUCT_UNAVAILABLE,"NO_DELIVERY_METHODS");
        List<Method> methods=new ArrayList<>();
        for (JsonNode entry:entries) {
            if (!"0".equals(entry.path("error_code").asText())) return rejection(entry);
            JsonNode money=entry.path("freight");
            String amount=money.path("amount").asText();
            String currency=money.path("currency_code").asText();String service=entry.path("service_name").asText();
            if (!amount.matches("[0-9]{1,12}(?:\\.[0-9]{1,8})?") || !currency.matches("[A-Z]{3}")
                    || !service.matches("[A-Za-z0-9_.-]{1,120}") || sensitive(service,token))
                return failure(INVALID_RESPONSE,"INVALID_METHOD_DATA");
            BigDecimal value=new BigDecimal(amount);
            // Keep amount exactly; never infer free shipping or convert currencies.
            if (money.hasNonNull("cent")) {
                String cent=money.path("cent").asText();
                if (!cent.matches("[0-9]{1,16}") || value.multiply(new BigDecimal("100")).compareTo(new BigDecimal(cent))!=0)
                    return failure(INVALID_RESPONSE,"CONFLICTING_MONETARY_FIELDS");
            }
            String estimate=entry.path("estimated_delivery_time").asText();
            if (!estimate.matches("[0-9]{1,4}(?:\\s*[-~]\\s*[0-9]{1,4})?\\s*(?:days?|d|dias?)?") || sensitive(estimate,token)) estimate=null;
            methods.add(new Method(service,value,currency,estimate));
        }
        if (methods.stream().map(Method::serviceName).distinct().count()!=methods.size()) return failure(INVALID_RESPONSE,"DUPLICATE_SERVICE");
        return result(SUCCESS,"CALL_SUCCEEDED",methods);
    }

    private boolean sensitive(String value,String token) { return value.contains(token) || value.contains(secret) || value.contains(appKey); }

    private AliExpressFreightDiagnosticResult rejection(JsonNode error) {
        String code=error.path("code").asText();
        if (code.isEmpty()) code=error.path("error_code").asText();
        String sub=error.path("sub_code").asText();
        if (code.equals("27") || code.equals("26") || Set.of("invalid-sessionkey","invalid-session","isv.invalid-session").contains(sub))
            return failure(TOKEN_REJECTED,"PROVIDER_TOKEN_REJECTED");
        if (code.equals("11") || Set.of("isv.permission-denied","isv.permission-api-package-empty","isv.permission-api-package-not-allowed","isv.permission-api-package-limit","isv.permission-ip-whitelist-limit").contains(sub))
            return failure(PERMISSION_DENIED,"PROVIDER_PERMISSION_DENIED");
        if (code.equals("25") || code.equals("22") || code.equals("29")) return failure(PROTOCOL_ERROR,"PROVIDER_PROTOCOL_REJECTED");
        if (code.equals("7")) return failure(RATE_LIMITED,"PROVIDER_RATE_LIMIT");
        if (Set.of("isv.invalid-parameter","isv.missing-parameter").contains(sub)) return failure(PARAMETER_ERROR,"PROVIDER_PARAMETER_REJECTED");
        // Do not guess permissions/product availability from free-form messages or unknown codes.
        return failure(PROVIDER_REJECTED,"UNCLASSIFIED_PROVIDER_REJECTION");
    }

    private static RestClient defaultClient() {
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build());factory.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder().baseUrl("https://eco.taobao.com").requestFactory(factory).build();
    }
}
