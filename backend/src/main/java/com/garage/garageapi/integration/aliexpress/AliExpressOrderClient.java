package com.garage.garageapi.integration.aliexpress;

import org.springframework.beans.factory.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** No retries, redirects, payment calls or provider body logging. Live contract is gated independently. */
@Component
public class AliExpressOrderClient {
    private final RestClient client;
    private final String key,secret;
    private final boolean enabled;
    @Autowired
    public AliExpressOrderClient(@Value("${aliexpress.app-key:}") String key,@Value("${aliexpress.app-secret:}") String secret,
            @Value("${aliexpress.orders.enabled:false}") boolean enabled,@Value("${aliexpress.orders.contract-confirmed:false}") boolean confirmed) {
        this(defaultClient(),key,secret,enabled && confirmed);
    }
    AliExpressOrderClient(RestClient client,String key,String secret,boolean enabled) {
        this.client=client;this.key=key;this.secret=secret;this.enabled=enabled;
    }
    public Result create(Map<String,Object> payload,String token) {
        if (!enabled || key.isBlank() || secret.isBlank() || token==null || token.isBlank()) return Result.unknown();
        try {
            Map<String,String> params=new TreeMap<>(Map.of("method","aliexpress.trade.buy.placeorder","app_key",key,
                    "session",token,"sign_method","sha256","timestamp",Long.toString(System.currentTimeMillis()),"v","2.0","format","json"));
            params.put("param_place_order_request4_open_api_d_t_o",JsonMapper.builder().build().writeValueAsString(payload));
            StringBuilder input=new StringBuilder();params.forEach((k,v)->input.append(k).append(v));
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            params.put("sign",HexFormat.of().withUpperCase().formatHex(mac.doFinal(input.toString().getBytes(StandardCharsets.UTF_8))));
            StringJoiner form=new StringJoiner("&");params.forEach((k,v)->form.add(URLEncoder.encode(k,StandardCharsets.UTF_8)+"="+URLEncoder.encode(v,StandardCharsets.UTF_8)));
            byte[] bytes=form.toString().getBytes(StandardCharsets.UTF_8);
            JsonNode body=client.post().uri("/sync").contentType(MediaType.APPLICATION_FORM_URLENCODED).accept(MediaType.APPLICATION_JSON)
                    .body(out->out.write(bytes)).retrieve().body(JsonNode.class);
            if (body==null || body.has("error_response")) return Result.unknown();
            JsonNode result=body.path("aliexpress_trade_buy_placeorder_response").path("result");
            JsonNode list=result.path("order_list");if(!list.isArray()) list=list.path("number");
            if (result.path("is_success").isBoolean() && result.path("is_success").asBoolean() && list.isArray() && !list.isEmpty() && list.size()<=1000) {
                List<String> ids=new ArrayList<>();
                for(JsonNode id:list) { if(!id.asText().matches("[0-9]{1,30}")) return Result.unknown();ids.add(id.asText()); }
                if(new HashSet<>(ids).size()!=ids.size()) return Result.unknown();
                return new Result(List.copyOf(ids),false);
            }
            // A partial order list, repeat-order error, timeout or unknown code is NEVER a retryable rejection.
            if (result.path("is_success").isBoolean() && !result.path("is_success").asBoolean()
                    && (list.isMissingNode() || list.isNull() || (list.isArray() && list.isEmpty()))
                    && Set.of("B_DROPSHIPPER_DELIVERY_ADDRESS_VALIDATE_FAIL","BLACKLIST_BUYER_IN_LIST","USER_ACCOUNT_DISABLED",
                        "PRICE_PAY_CURRENCY_ERROR","DELIVERY_METHOD_NOT_EXIST").contains(result.path("error_code").asText()))
                return new Result(List.of(),true);
        } catch (Exception ignored) { /* A request may have reached the supplier; never retry or echo diagnostics. */ }
        return Result.unknown();
    }
    public record Result(List<String> orderIds,boolean rejected) {
        public static Result unknown() { return new Result(List.of(),false); }
    }
    private static RestClient defaultClient() {
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(25));
        return RestClient.builder().baseUrl("https://api-sg.aliexpress.com").requestFactory(factory).build();
    }
}
