package com.garage.garageapi.payment.reconciliation;

import com.garage.garageapi.payment.gateway.PaymentProviderException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;

/** Search discovers IDs only. Each discovered payment must still pass the authenticated lookup/updater. */
@Component
public class MercadoPagoPaymentSearchClient {
    private final RestClient http;
    private final String token;
    @Autowired
    public MercadoPagoPaymentSearchClient(@Value("${mercadopago.base-url:https://api.mercadopago.com}") String baseUrl,
                                         @Value("${mercadopago.access-token:}") String token) {
        this(client(baseUrl),token);
    }
    MercadoPagoPaymentSearchClient(RestClient http,String token) { this.http=http;this.token=token; }
    public List<String> findIds(String reference) {
        if(token.isBlank() || reference==null || !reference.matches("garage_order_[0-9]+_payment_[0-9]+"))
            throw new PaymentProviderException("Consulta de recuperação não configurada");
        JsonNode body=http.get().uri(u->u.path("/v1/payments/search").queryParam("external_reference",reference)
                        .queryParam("sort","date_last_updated").queryParam("criteria","desc")
                        .queryParam("limit",50).queryParam("offset",0).build())
                .header("Authorization","Bearer "+token).accept(MediaType.APPLICATION_JSON)
                .retrieve().body(JsonNode.class);
        if(body==null || !body.path("results").isArray())throw new PaymentProviderException("Resposta de recuperação inválida");
        if(body.path("paging").path("total").asInt()>50)throw new AmbiguousPaymentsException();
        var ids=new LinkedHashSet<String>();
        for(var result:body.path("results")) {
            if(!reference.equals(result.path("external_reference").asText()))
                throw new PaymentProviderException("Referência divergente na recuperação");
            String id=result.path("id").asText();
            if(!id.matches("[0-9]{1,30}"))throw new PaymentProviderException("Identificador inválido na recuperação");
            ids.add(id);
        }
        return List.copyOf(ids);
    }
    static class AmbiguousPaymentsException extends RuntimeException { }
    private static RestClient client(String baseUrl) {
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build());factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
