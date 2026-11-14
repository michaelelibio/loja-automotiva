package com.garage.garageapi.integration.aliexpress;

import org.springframework.beans.factory.annotation.*;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Exactly one HTTP invocation; no redirects, retries, payment calls or provider-body logging. */
@Component
public class AliExpressOrderClient {
    private final RestClient client;
    private final String key,secret;
    private final boolean enabled;
    private boolean administrativeEnabled;
    @Autowired
    public AliExpressOrderClient(@Value("${aliexpress.app-key:}") String key,@Value("${aliexpress.app-secret:}") String secret,
            @Value("${aliexpress.orders.enabled:false}") boolean enabled,@Value("${aliexpress.orders.contract-confirmed:false}") boolean confirmed,
            AliExpressPlaceOrderPermission permission,@Value("${aliexpress.admin-tests.real-enabled:false}") boolean administrativeEnabled){
        this(defaultClient(),key,secret,enabled && confirmed && permission.verified());this.administrativeEnabled=administrativeEnabled;
    }
    AliExpressOrderClient(RestClient client,String key,String secret,boolean enabled){this.client=client;this.key=key;this.secret=secret;this.enabled=enabled;}
    public Result create(Map<String,Object> payload,String token){return enabled?submit(payload,token):Result.unknown();}
    public Result createAdministrative(Long id,Map<String,Object> payload,String token){
        return Long.valueOf(19).equals(id) && administrativeEnabled?submit(payload,token):Result.unknown();
    }
    private Result submit(Map<String,Object> payload,String token){
        if(key.isBlank() || secret.isBlank() || token==null || token.isBlank())return new Result(List.of(),true,false,new ProviderError("LOCAL_CREDENTIALS_MISSING",null,"Credenciais OAuth ou aplicação indisponíveis.",null));
        var sensitive=new ArrayList<>(List.of(key,secret,token));collect(payload,sensitive);
        try {
            Map<String,String> params=new TreeMap<>(Map.of("method","aliexpress.trade.buy.placeorder","app_key",key,"session",token,"sign_method","sha256","timestamp",Long.toString(System.currentTimeMillis()),"v","2.0","format","json"));
            params.put("param_place_order_request4_open_api_d_t_o",JsonMapper.builder().build().writeValueAsString(payload));
            StringBuilder input=new StringBuilder();params.forEach((k,v)->input.append(k).append(v));Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            params.put("sign",HexFormat.of().withUpperCase().formatHex(mac.doFinal(input.toString().getBytes(StandardCharsets.UTF_8))));
            StringJoiner form=new StringJoiner("&");params.forEach((k,v)->form.add(URLEncoder.encode(k,StandardCharsets.UTF_8)+"="+URLEncoder.encode(v,StandardCharsets.UTF_8)));byte[] bytes=form.toString().getBytes(StandardCharsets.UTF_8);
            var response=client.post().uri("/sync").contentType(MediaType.APPLICATION_FORM_URLENCODED).accept(MediaType.APPLICATION_JSON).body(out->out.write(bytes)).retrieve().toEntity(JsonNode.class);
            return parse(response.getBody(),response.getStatusCode().value(),sensitive);
        } catch(RestClientResponseException ex){
            try{String raw=ex.getResponseBodyAsString();if(raw.length()<=1_048_576)return parse(JsonMapper.builder().build().readTree(raw),ex.getStatusCode().value(),sensitive);}catch(RuntimeException ignored){}
            return new Result(List.of(),false,false,new ProviderError("HTTP_"+ex.getStatusCode().value(),null,"AliExpress retornou erro HTTP sem uma resposta estruturada reconhecida. Reconcilie antes de tentar novamente.",ex.getStatusCode().value()));
        } catch(Exception ignored){return Result.unknown();}
    }
    private Result parse(JsonNode body,int httpStatus,List<String> sensitive){
        if(body==null || !body.isObject())return Result.unknown();JsonNode response=body.path("aliexpress_trade_buy_placeorder_response");if(response.isMissingNode())response=body;
        JsonNode result=response.path("result");JsonNode list=result.path("order_list");if(!list.isArray())list=list.path("number");
        List<String> ids=new ArrayList<>();boolean complete=list.isArray() && !list.isEmpty() && list.size()<=1000;
        if(list.isArray())for(int i=0;i<Math.min(list.size(),1000);i++){var id=list.get(i);String value=id.asText();if(!(id.isString() || id.isIntegralNumber()) || !value.matches("[1-9][0-9]{0,29}") || ids.contains(value))complete=false;else ids.add(value);}
        JsonNode error=body.path("error_response");if(!error.isObject())error=response.path("error_response");ProviderError failure=null;
        if(error.isObject())failure=error(error.path("code").asText(),error.path("sub_code").asText(),join(error.path("msg").asText(),error.path("sub_msg").asText()),httpStatus,sensitive);
        else if(nonzero(result.path("error_code")))failure=error(result.path("error_code").asText(),null,result.path("error_msg").asText(),httpStatus,sensitive);
        else if(nonzero(response.path("code")) || nonzero(body.path("code")))failure=error(nonzero(response.path("code"))?response.path("code").asText():body.path("code").asText(),null,join(response.path("msg").asText(),body.path("msg").asText()),httpStatus,sensitive);
        else if(httpStatus<200 || httpStatus>=300)failure=error("HTTP_"+httpStatus,null,"AliExpress retornou erro HTTP.",httpStatus,sensitive);
        boolean success=result.path("is_success").isBoolean()?result.path("is_success").asBoolean():"true".equals(result.path("is_success").asText());
        boolean rejected=ids.isEmpty() && failure!=null && !failure.code().startsWith("UNRECOGNIZED") && !failure.code().startsWith("HTTP_") && !(failure.code().toUpperCase(Locale.ROOT).contains("REPEAT") || Objects.toString(failure.subCode(),"").toUpperCase(Locale.ROOT).contains("REPEAT"));
        if(failure==null && !success && result.has("is_success"))failure=error("CREATE_NOT_ACCEPTED",null,result.path("error_msg").asText(),httpStatus,sensitive);
        return new Result(List.copyOf(ids),rejected,complete && success && failure==null,failure);
    }
    private static boolean nonzero(JsonNode n){return !n.isMissingNode() && !n.isNull() && !n.asText().isBlank() && !"0".equals(n.asText());}
    private static ProviderError error(String code,String sub,String message,int status,List<String> sensitive){return new ProviderError(safeCode(code,sensitive),sub==null || sub.isBlank()?null:safeCode(sub,sensitive),sanitize(message,sensitive),status);}
    private static String safeCode(String value,List<String> sensitive){if(value==null || !value.matches("[A-Za-z0-9_.:-]{1,120}"))return "UNRECOGNIZED_PROVIDER_ERROR";for(String item:sensitive)if(item!=null && item.length()>=4 && value.toLowerCase(Locale.ROOT).contains(item.toLowerCase(Locale.ROOT)))return "REDACTED_PROVIDER_CODE";return value;}
    private static String join(String a,String b){return a==null || a.isBlank()?Objects.toString(b,""):b==null || b.isBlank()?a:a+" — "+b;}
    static String sanitize(String raw,List<String> sensitive){
        if(raw==null || raw.isBlank())return "AliExpress recusou a solicitação; consulte o código oficial informado.";
        String value=raw.length()>4000?raw.substring(0,4000):raw;
        for(String item:sensitive)if(item!=null && item.length()>=4)value=value.replaceAll("(?i)"+java.util.regex.Pattern.quote(item),"[redacted]").replaceAll("(?i)"+java.util.regex.Pattern.quote(URLEncoder.encode(item,StandardCharsets.UTF_8)),"[redacted]");
        value=value.replaceAll("(?i)https?://[^\\s<>]+","[URL removida]").replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}","[email removido]")
                .replaceAll("(?i)(access[_ -]?token|refresh[_ -]?token|app[_ -]?(?:secret|key)|authorization|session|password|secret|sign|user_id|account_id|email|cpf|mobile_no|phone|birthday|birth_date)\\s*[:=]\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s,;}]+)","$1=[removido]")
                .replaceAll("(?<![0-9])[0-9]{3}\\.?[0-9]{3}\\.?[0-9]{3}[-.]?[0-9]{2}(?![0-9])","[identificador removido]")
                .replaceAll("(?<![0-9])(?:\\+55[ -]?)?\\(?[0-9]{2}\\)?[ -]?[0-9]{4,5}[ -]?[0-9]{4}(?![0-9])","[telefone removido]")
                .replaceAll("[A-Za-z0-9_+/=-]{32,}","[valor removido]").replaceAll("[\\p{Cntrl}]"," ").trim();
        // Address/name fragments cannot safely be inferred from arbitrary supplier prose.
        if(value.matches("(?is).*(?:\\b(?:address|endereço|recipient|receiver|contact_person|full_name|passport|street|buyer|user|account|customer|name)\\b|[{}<>]).*"))
            return "Mensagem oficial omitida por conter dados pessoais ou estrutura sensível; consulte o código informado.";
        return value.length()>600?value.substring(0,600):value;
    }
    private static void collect(Object value,List<String> sensitive){if(value instanceof Map<?,?> map)map.values().forEach(v->collect(v,sensitive));else if(value instanceof Iterable<?> values)values.forEach(v->collect(v,sensitive));else if(value instanceof String text)sensitive.add(text);}
    public record ProviderError(String code,String subCode,String message,Integer httpStatus){}
    public record Result(List<String> orderIds,boolean rejected,boolean confirmed,ProviderError providerError){
        public Result(List<String> ids,boolean rejected){this(ids,rejected,!ids.isEmpty() && !rejected,null);}
        public Result(List<String> ids,boolean rejected,boolean confirmed){this(ids,rejected,confirmed,null);}
        public static Result unknown(){return new Result(List.of(),false,false,null);}
    }
    private static RestClient defaultClient(){var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build());factory.setReadTimeout(Duration.ofSeconds(25));return RestClient.builder().baseUrl("https://api-sg.aliexpress.com").requestFactory(factory).build();}
}
