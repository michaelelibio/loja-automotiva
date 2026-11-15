package com.garage.garageapi.shipping.commercial;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import tools.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Duration;
import static com.garage.garageapi.shipping.commercial.CommercialFreightPolicy.require;
@Component
public class PostalStateResolver {
    private record Cached(String state,java.time.Instant expires){}
    private final java.util.concurrent.ConcurrentHashMap<String,Cached> cache=new java.util.concurrent.ConcurrentHashMap<>();
    private final RestClient client;
    public PostalStateResolver(){
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));client=RestClient.builder().baseUrl("https://viacep.com.br").requestFactory(factory).build();
    }
    PostalStateResolver(RestClient client){this.client=client;}
    public String resolve(String zip){
        require(zip!=null && zip.matches("[0-9]{8}"),"CEP inválido.");
        Cached cached=cache.get(zip);if(cached!=null && java.time.Instant.now().isBefore(cached.expires()))return cached.state();
        try {
            JsonNode node=client.get().uri("/ws/{zip}/json/",zip).retrieve().body(JsonNode.class);
            String state=node==null?null:node.path("uf").asText();
            require(node!=null && !node.path("erro").asBoolean(false) && zip.equals(node.path("cep").asText().replace("-",""))
                && com.garage.garageapi.integration.aliexpress.AliExpressDsFreightClient.BRAZILIAN_PROVINCES.containsKey(state),"CEP sem UF confirmada.");
            if(cache.size()>=256)cache.clear();cache.put(zip,new Cached(state,java.time.Instant.now().plusSeconds(3600)));return state;
        } catch(RuntimeException ignored){throw new com.garage.garageapi.shared.exception.ResourceConflictException("Não foi possível confirmar a UF do CEP. Solicite cotação assistida.");}
    }
}
