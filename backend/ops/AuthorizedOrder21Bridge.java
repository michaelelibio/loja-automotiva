package com.garage.garageapi.integration.aliexpress;

import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import tools.jackson.databind.json.JsonMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/** One-shot authorized #21 only. Secrets/payload arrive over stdin, never command line or disk. */
class AuthorizedOrder21Bridge {
    public static void main(String[] args) throws Exception {
        var mapper=JsonMapper.builder().build();var input=mapper.readTree(System.in.readAllBytes());
        if(input.path("orderId").asInt()!=21)throw new IllegalArgumentException("Order scope");
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build());factory.setReadTimeout(Duration.ofSeconds(25));
        var http=RestClient.builder().baseUrl("https://api-sg.aliexpress.com").requestFactory(factory).build();
        String key=input.path("key").asText(),secret=input.path("secret").asText(),token=input.path("token").asText();
        Object result;
        if("create".equals(input.path("action").asText())) {
            Map<String,Object> payload=mapper.convertValue(input.path("payload"),new tools.jackson.core.type.TypeReference<Map<String,Object>>(){});
            if(!"INGARAGE-21".equals(payload.get("out_order_id")))throw new IllegalArgumentException("Reference scope");
            result=new AliExpressOrderClient(http,key,secret,true).create(payload,token);
        } else if("query".equals(input.path("action").asText())) {
            result=new AliExpressOrderQueryClient(http,key,secret,true).query(input.path("supplierId").asText(),token);
        } else throw new IllegalArgumentException("Action scope");
        System.out.println(mapper.writeValueAsString(result));
    }
}
