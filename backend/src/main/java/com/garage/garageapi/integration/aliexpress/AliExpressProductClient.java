package com.garage.garageapi.integration.aliexpress;

import org.springframework.beans.factory.annotation.*;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import tools.jackson.databind.JsonNode;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static com.garage.garageapi.integration.aliexpress.AliExpressIntegrationException.Reason.*;

@Component
public class AliExpressProductClient {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AliExpressProductClient.class);
    private final RestClient client;
    private final String appKey, secret;
    @Autowired
    public AliExpressProductClient(@Value("${aliexpress.app-key:}") String appKey,
                                  @Value("${aliexpress.app-secret:}") String secret) {
        this(defaultClient(), appKey, secret);
    }
    AliExpressProductClient(RestClient client, String appKey, String secret) {
        this.client = client; this.appKey = appKey; this.secret = secret;
    }
    public JsonNode get(String productId, String country, String currency, String language, String token) {
        if (appKey.isBlank() || secret.isBlank() || appKey.contains("COLE_AQUI") || secret.contains("COLE_AQUI")) throw new AliExpressIntegrationException(NOT_CONFIGURED);
        try {
            Map<String, String> params = new TreeMap<>(Map.of("method", "aliexpress.ds.product.get", "app_key", appKey,
                    "session", token, "sign_method", "sha256", "timestamp", Long.toString(System.currentTimeMillis()),
                    "v", "2.0", "format", "json", "product_id", productId, "ship_to_country", country));
            params.put("target_currency", currency); params.put("target_language", language);
            // Official SDK Protocol.TOP: empty signing prefix, session, /sync and epoch millis.
            StringBuilder input = new StringBuilder(); params.forEach((k,v) -> input.append(k).append(v));
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            params.put("sign", HexFormat.of().withUpperCase().formatHex(mac.doFinal(input.toString().getBytes(StandardCharsets.UTF_8))));
            StringJoiner form = new StringJoiner("&");
            params.forEach((k,v) -> form.add(URLEncoder.encode(k, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(v, StandardCharsets.UTF_8)));
            byte[] bytes = form.toString().getBytes(StandardCharsets.UTF_8);
            JsonNode body = client.post().uri("/sync").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON).body(out -> out.write(bytes)).retrieve().body(JsonNode.class);
            if (body == null || !body.isObject()) throw incomplete("response_body", "$", body);
            JsonNode error = body.path("error_response");
            if (!error.isMissingNode()) throw providerError(error.path("code").asText(), error.path("sub_code").asText());
            JsonNode response = body.path("aliexpress_ds_product_get_response");
            if (response.isMissingNode()) response = body;
            if (response.has("code") && !"0".equals(response.path("code").asText())) throw providerError(response.path("code").asText(), "");
            if (!"200".equals(response.path("rsp_code").asText())) throw providerError(response.path("rsp_code").asText(), response.path("rsp_msg").asText());
            JsonNode result = response.path("result");
            if (!result.isObject()) throw incomplete("response_result", "result", result);
            return result;
        } catch (AliExpressIntegrationException ex) { throw ex; }
        catch (RestClientResponseException ex) {
            throw new AliExpressIntegrationException(switch (ex.getStatusCode().value()) {
                case 401 -> AUTHORIZATION_REQUIRED; case 403 -> PERMISSION; case 404 -> NOT_FOUND; case 429 -> RATE_LIMIT; default -> COMMUNICATION;
            });
        } catch (Exception ex) {
            Throwable cause = ex;
            while (cause != null) {
                if (cause instanceof java.net.http.HttpTimeoutException || cause instanceof java.net.SocketTimeoutException) throw new AliExpressIntegrationException(TIMEOUT);
                cause = cause.getCause();
            }
            throw new AliExpressIntegrationException(COMMUNICATION);
        }
    }
    private static AliExpressIntegrationException incomplete(String stage, String field, JsonNode node) {
        LOG.warn("AliExpress preview incomplete; stage={}; field={}; condition=expected_object; actualType={}",
                stage, field, node == null ? "NO_BODY" : node.getNodeType().name());
        return new AliExpressIntegrationException(INCOMPLETE);
    }
    private AliExpressIntegrationException providerError(String code, String subCode) {
        String label = (code + " " + subCode).toUpperCase(Locale.ROOT);
        var reason = label.contains("481") || label.contains("SHIP_TO_COUNTRY") || label.contains("NOT ALLOWED TO THIS COUNTRY") ? COUNTRY_REJECTED
                : label.contains("ITEM_ID_NOT_FOUND") || code.equals("404") ? NOT_FOUND
                : label.contains("SESSION") || label.contains("TOKEN") || code.equals("27") ? AUTHORIZATION_REQUIRED
                : label.contains("PERMISSION") || label.contains("INSUFFICIENT") ? PERMISSION
                : label.contains("LIMIT") || label.contains("FREQUENCY") || code.equals("429") ? RATE_LIMIT : COMMUNICATION;
        // Never echo provider messages, request IDs, URLs or error bodies.
        return new AliExpressIntegrationException(reason);
    }
    private static RestClient defaultClient() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder().baseUrl("https://api-sg.aliexpress.com").requestFactory(factory).build();
    }
}
