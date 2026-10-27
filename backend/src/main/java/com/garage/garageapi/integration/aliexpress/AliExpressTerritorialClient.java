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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.*;
import java.util.*;
import static com.garage.garageapi.integration.aliexpress.AliExpressTerritorialDiagnosticResult.*;
import static com.garage.garageapi.integration.aliexpress.AliExpressFreightDiagnosticResult.Status.*;

/** Fixed BR read-only method. No refresh, retries, freight, order or payment calls. */
@Component
public class AliExpressTerritorialClient {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private static final int MAX_RESPONSE=4*1024*1024;
    private static final Set<String> IDENTIFIERS=Set.of("id","code","value","parentId","parent_id",
            "country","countryCode","provinceCode","cityCode","province_code","city_code","type");
    private static final Set<String> LABELS=Set.of("name","label","value","enName","localName","englishName",
            "displayName","provinceName","cityName","stateName");
    private final RestClient client;
    private final String appKey,secret;
    private final Clock clock;
    @Autowired
    public AliExpressTerritorialClient(@Value("${aliexpress.app-key:}") String appKey,
            @Value("${aliexpress.app-secret:}") String secret) {
        this(defaultClient(),appKey,secret,Clock.systemUTC());
    }
    AliExpressTerritorialClient(RestClient client,String appKey,String secret,Clock clock) {
        this.client=client;this.appKey=appKey;this.secret=secret;this.clock=clock;
    }
    public AliExpressTerritorialDiagnosticResult query(String token) {
        if (appKey.isBlank() || secret.isBlank() || appKey.contains("COLE_AQUI") || secret.contains("COLE_AQUI"))
            return failure(NOT_CONFIGURED,"APPLICATION_NOT_CONFIGURED");
        if (token==null || token.isBlank()) return failure(LOCAL_AUTHORIZATION_UNAVAILABLE,"LOCAL_TOKEN_UNAVAILABLE");
        try {
            Map<String,String> params=new TreeMap<>();
            params.put("method","aliexpress.ds.address.get");params.put("app_key",appKey);params.put("session",token);
            params.put("sign_method","sha256");params.put("timestamp",Long.toString(clock.millis()));
            params.put("v","2.0");params.put("format","json");params.put("simplify","true");
            // Optional language omitted: no undocumented pt_BR/PT language mapping assumed.
            params.put("countryCode","BR");params.put("isMultiLanguage","true");
            StringBuilder canonical=new StringBuilder();params.forEach((k,v)->canonical.append(k).append(v));
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            params.put("sign",HexFormat.of().withUpperCase().formatHex(mac.doFinal(canonical.toString().getBytes(StandardCharsets.UTF_8))));
            StringJoiner form=new StringJoiner("&");params.forEach((k,v)->form.add(URLEncoder.encode(k,StandardCharsets.UTF_8)+"="+URLEncoder.encode(v,StandardCharsets.UTF_8)));
            byte[] requestBody=form.toString().getBytes(StandardCharsets.UTF_8);
            return client.post().uri("/sync").contentType(MediaType.APPLICATION_FORM_URLENCODED).accept(MediaType.APPLICATION_JSON)
                    .body(out->out.write(requestBody)).exchange((request,response)->{
                        int status=response.getStatusCode().value();
                        if (status==429) return failure(RATE_LIMITED,"HTTP_RATE_LIMIT");
                        if (status<200 || status>=300) return failure(COMMUNICATION_ERROR,"HTTP_FAILURE");
                        byte[] bytes=response.getBody().readNBytes(MAX_RESPONSE+1);
                        if (bytes.length>MAX_RESPONSE) return failure(INVALID_RESPONSE,"RESPONSE_TOO_LARGE");
                        final JsonNode body;
                        try { body=JSON.readTree(bytes); }
                        catch(Exception ignored) { return failure(INVALID_RESPONSE,"INVALID_JSON_RESPONSE"); }
                        return parse(body,token);
                    });
        } catch(Exception ignored) { return failure(COMMUNICATION_ERROR,"COMMUNICATION_FAILURE"); }
    }
    private AliExpressTerritorialDiagnosticResult parse(JsonNode body,String token) {
        if (body==null || !body.isObject()) return failure(INVALID_RESPONSE,"INVALID_RESPONSE_SHAPE");
        JsonNode error=body.path("error_response");
        if (error.isObject()) {
            String code=error.path("code").asText(),sub=error.path("sub_code").asText();
            return platformFailure(code,sub).withProviderCodes(safeProviderCode(code,token),safeProviderCode(sub,token));
        }
        JsonNode result=body.path("aliexpress_ds_address_get_response").path("result");
        if (result.isMissingNode()) result=body.path("result");
        String ret=result.path("ret").asText();
        if (!(result.path("ret").isBoolean() || result.path("ret").isString()) || !Set.of("true","false").contains(ret))
            return failure(INVALID_RESPONSE,"INVALID_RESPONSE_SHAPE");
        if (!ret.equals("true")) return failure(PROVIDER_REJECTED,"ADDRESS_QUERY_REJECTED")
                .withProviderCodes(safeProviderCode(result.path("code").asText(),token),null);
        JsonNode data=result.path("data");
        if (!data.isObject() || !data.path("country").isString() || !data.path("country").asText().equals("BR"))
            return failure(INVALID_RESPONSE,"COUNTRY_NOT_CONFIRMED");
        if (!(data.path("children").isString() || data.path("children").isContainer()))
            return of(INVALID_RESPONSE,"UNSUPPORTED_TERRITORIAL_STRUCTURE",true,"BR",List.of(),List.of());
        Scan scan=new Scan(token);
        try { scan.walk(data,0,List.of()); }
        catch(Exception ignored) { return of(INVALID_RESPONSE,"UNSUPPORTED_TERRITORIAL_STRUCTURE",true,"BR",List.of(),List.of()); }
        return of(SUCCESS,"TERRITORIAL_QUERY_SUCCEEDED",true,"BR",scan.matches,new ArrayList<>(scan.fieldNames));
    }
    private AliExpressTerritorialDiagnosticResult platformFailure(String code,String sub) {
            if (Set.of("21","22","24","25","28","29").contains(code)) return failure(PROTOCOL_ERROR,"TOP_"+code);
            // Observed AE error_response code; does not establish a missing app grant.
            if (code.equals("IllegalAccessToken")) return failure(TOKEN_REJECTED,"PROVIDER_TOKEN_REJECTED");
            if (code.equals("11") || Set.of("isv.insufficient-permission","isv.permission-denied","isv.permission-api-package-not-allowed","isv.permission-api-package-empty","isv.permission-api-package-limit","isv.permission-ip-whitelist-limit").contains(sub))
                return failure(PERMISSION_DENIED,"PROVIDER_PERMISSION_DENIED");
            if (Set.of("26","27").contains(code) || Set.of("invalid-sessionkey","isv.invalid-session").contains(sub))
                return failure(TOKEN_REJECTED,"PROVIDER_TOKEN_REJECTED");
            if (code.equals("7")) return failure(RATE_LIMITED,"PROVIDER_RATE_LIMIT");
            return failure(PROVIDER_REJECTED,"UNCLASSIFIED_PROVIDER_REJECTION");
    }
    private String safeProviderCode(String code,String token) {
        return !sensitive(code,token) && code.matches("(?:[0-9]{1,6}|[A-Z][A-Z0-9_]{1,63}|(?:isv|isp)\\.[a-z][a-z0-9.-]{1,63}|[A-Z][a-z]+(?:[A-Z][a-z]+){1,8})")?code:null;
    }
    private final class Scan {
        final String token;
        int count;
        final List<Match> matches=new ArrayList<>();
        final Set<String> fieldNames=new TreeSet<>();
        Scan(String token) { this.token=token; }
        void walk(JsonNode node,int depth,List<Node> ancestors) {
            if (++count>100000 || depth>24 || matches.size()>30) throw new IllegalArgumentException();
            if (node.isString()) {
                // children is documented as a JSON String; nested JSON may also encode levels.
                String text=node.asText().strip();
                if (text.startsWith("[") || text.startsWith("{")) walk(JSON.readTree(text),depth+1,ancestors);
                else throw new IllegalArgumentException();
            } else if (node.isArray()) {
                for (JsonNode child:node) walk(child,depth+1,ancestors);
            } else if (node.isObject()) {
                List<Scalar> fields=new ArrayList<>();
                Set<String> targets=new TreeSet<>();
                for (var property:node.properties()) {
                    String key=property.getKey();JsonNode value=property.getValue();
                    if (IDENTIFIERS.contains(key) || LABELS.contains(key) || key.equals("children")) fieldNames.add(key);
                    if (!(value.isString() || value.isIntegralNumber())) continue;
                    String text=value.asText();
                    if (sensitive(text,token)) continue;
                    if (IDENTIFIERS.contains(key) && text.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,79}"))
                        fields.add(new Scalar(key,value.isString()?"STRING":"NUMBER",text));
                    else if (LABELS.contains(key) && text.matches("[\\p{L}][\\p{L} .'-]{0,79}"))
                        fields.add(new Scalar(key,"STRING",text));
                    if (LABELS.contains(key)) {
                        String folded=Normalizer.normalize(text,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);
                        if (Set.of("santa catarina","joinville").contains(folded)) targets.add(folded.equals("joinville")?"JOINVILLE":"SANTA_CATARINA");
                    }
                }
                Node observed=new Node(fields);
                for(String target:targets) {
                    if (matches.size()>=30) throw new IllegalArgumentException();
                    matches.add(new Match(target,depth,ancestors,observed));
                }
                List<Node> next=new ArrayList<>(ancestors);
                if (!fields.isEmpty()) next.add(observed);
                for(var property:node.properties()) {
                    JsonNode child=property.getValue();
                    if (child.isContainer() || property.getKey().equals("children")) walk(child,depth+1,next);
                }
            }
        }
    }
    private boolean sensitive(String value,String token) { return value.contains(token) || value.contains(secret) || value.contains(appKey); }
    private static RestClient defaultClient() {
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build());factory.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder().baseUrl("https://api-sg.aliexpress.com").requestFactory(factory).build();
    }
}
