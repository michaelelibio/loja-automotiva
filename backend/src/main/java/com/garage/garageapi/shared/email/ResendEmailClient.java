package com.garage.garageapi.shared.email;

import com.garage.garageapi.auth.exception.AccountEmailDeliveryException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.account.email.mode", havingValue = "resend")
public class ResendEmailClient {
    private final RestClient restClient;
    private final String apiKey;
    private final String from;

    @Autowired
    public ResendEmailClient(@Value("${app.account.email.resend.api-key:}") String apiKey,
                             @Value("${app.account.email.from:onboarding@resend.dev}") String from) {
        this(RestClient.builder().baseUrl("https://api.resend.com").build(), apiKey, from);
    }

    public ResendEmailClient(RestClient restClient, String apiKey, String from) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("RESEND_API_KEY é obrigatória no modo resend");
        if (from == null || from.isBlank()) throw new IllegalStateException("ACCOUNT_EMAIL_FROM é obrigatório no modo resend");
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.from = from;
    }

    public void send(String recipient, String subject, String html, String text) {
        try {
            restClient.post().uri("/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SendEmailRequest(from, List.of(recipient), subject, html, text))
                    .retrieve().toBodilessEntity();
        } catch (RestClientException exception) {
            throw new AccountEmailDeliveryException();
        }
    }

    public record SendEmailRequest(String from, List<String> to, String subject, String html, String text) { }
}
