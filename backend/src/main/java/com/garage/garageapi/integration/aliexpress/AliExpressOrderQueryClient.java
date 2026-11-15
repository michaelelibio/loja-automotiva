package com.garage.garageapi.integration.aliexpress;

import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Read-only method allowlisted in code. No order creation, payment, redirects or retries. */
@Component
public class AliExpressOrderQueryClient {
    public static final String METHOD="aliexpress.ds.trade.order.get";
    private final RestClient http;
    private final String key,secret;
    private final boolean enabled;
    private boolean administrativeEnabled;
    @Autowired
    public AliExpressOrderQueryClient(@Value("${aliexpress.app-key:}") String key,
            @Value("${aliexpress.app-secret:}") String secret,
            @Value("${aliexpress.orders.sync-enabled:false}") boolean enabled,
            @Value("${aliexpress.orders.sync-contract-confirmed:false}") boolean confirmed,
            AliExpressOrderQueryPermission permission,@Value("${aliexpress.admin-tests.real-enabled:false}") boolean administrativeEnabled) {
        this(defaultClient(),key,secret,enabled && confirmed && permission.verified());
        this.administrativeEnabled=administrativeEnabled;
    }
    AliExpressOrderQueryClient(RestClient http,String key,String secret,boolean enabled) {
        this.http=http;this.key=key;this.secret=secret;this.enabled=enabled;
    }
    public AliExpressSupplierSnapshot.Part query(String id,String token) {
        if(!enabled) return AliExpressSupplierSnapshot.unknown(id,"LOOKUP_BLOCKED");
        return lookup(id,token);
    }
    public AliExpressSupplierSnapshot.Part queryAdministrative(Long orderId,String id,String token) {
        if(!Long.valueOf(19).equals(orderId) || !administrativeEnabled) return AliExpressSupplierSnapshot.unknown(id,"LOOKUP_BLOCKED");
        return lookup(id,token);
    }
    private AliExpressSupplierSnapshot.Part lookup(String id,String token) {
        if (id==null || !id.matches("[0-9]{1,30}") || key.isBlank() || secret.isBlank()
                || token==null || token.isBlank()) return AliExpressSupplierSnapshot.unknown(id,"LOOKUP_BLOCKED");
        try {
            Map<String,String> p=new TreeMap<>(Map.of("method",METHOD,"app_key",key,"session",token,
                    "sign_method","sha256","v","2.0","format","json","order_id",id,"timestamp",
                    Long.toString(System.currentTimeMillis())));
            StringBuilder canonical=new StringBuilder();p.forEach((k,v)->canonical.append(k).append(v));
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            p.put("sign",HexFormat.of().withUpperCase().formatHex(mac.doFinal(canonical.toString().getBytes(StandardCharsets.UTF_8))));
            StringJoiner form=new StringJoiner("&");p.forEach((k,v)->form.add(URLEncoder.encode(k,StandardCharsets.UTF_8)+"="+URLEncoder.encode(v,StandardCharsets.UTF_8)));
            byte[] bytes=form.toString().getBytes(StandardCharsets.UTF_8);
            JsonNode body=http.post().uri("/sync").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON).body(out->out.write(bytes)).retrieve().body(JsonNode.class);
            if (body==null || body.has("error_response") || (body.hasNonNull("code") && !"0".equals(body.path("code").asText()))) return AliExpressSupplierSnapshot.unknown(id,"LOOKUP_FAILED");
            var envelope=body.path("aliexpress_ds_trade_order_get_response");
            if(envelope.isMissingNode()) envelope=body;
            // Never accept an error envelope just because it also contains a result.
            if (envelope.has("error_response") || (envelope.hasNonNull("code") && !"0".equals(envelope.path("code").asText()))
                    || !"200".equals(envelope.path("rsp_code").asText()))
                return AliExpressSupplierSnapshot.unknown(id,"LOOKUP_FAILED");
            var result=envelope.path("result");
            if (!result.isObject() || !result.path("order_status").isString()) return AliExpressSupplierSnapshot.unknown(id,"INVALID_RESPONSE");
            if (result.hasNonNull("order_id") && !id.equals(result.path("order_id").asText()))
                return AliExpressSupplierSnapshot.unknown(id,"ORDER_MISMATCH");
            return AliExpressSupplierSnapshot.classify(id,result.path("order_status").asText(),
                    result.path("logistics_status").isString() ? result.path("logistics_status").asText() : null).withCosts(costs(result));
        } catch (Exception ignored) { return AliExpressSupplierSnapshot.unknown(id,"LOOKUP_FAILED"); }
    }
    static AliExpressSupplierCosts costs(JsonNode result) {
        var money=result.path("order_amount");String amount=money.path("amount").asText(),currency=money.path("currency_code").asText();
        if(!amount.matches("[0-9]{1,10}(\\.[0-9]{1,4})?") || !currency.matches("[A-Z]{3}"))return null;
        return new AliExpressSupplierCosts("SUPPLIER_ORDER_API",currency,null,null,null,new java.math.BigDecimal(amount),Instant.now());
    }
    private static RestClient defaultClient() {
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(8));
        return RestClient.builder().baseUrl("https://api-sg.aliexpress.com").requestFactory(factory).build();
    }
}
