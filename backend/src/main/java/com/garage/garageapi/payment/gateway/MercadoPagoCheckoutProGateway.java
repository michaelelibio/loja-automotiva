package com.garage.garageapi.payment.gateway;

import com.garage.garageapi.payment.entity.PaymentStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MercadoPagoCheckoutProGateway implements CheckoutProGateway {
    private static final Logger logger = LoggerFactory.getLogger(MercadoPagoCheckoutProGateway.class);
    private static final String CREATE_PREFERENCE = "/checkout/preferences";
    private static final String FIND_PAYMENT = "/v1/payments/{id}";

    private final RestClient restClient;
    private final String accessToken;
    private final String successUrl;
    private final String pendingUrl;
    private final String failureUrl;
    private final String notificationUrl;
    private final boolean sandbox;
    private final boolean diagnosticLogging;

    @Autowired
    public MercadoPagoCheckoutProGateway(
            @Value("${mercadopago.base-url:https://api.mercadopago.com}") String baseUrl,
            @Value("${mercadopago.access-token:}") String accessToken,
            @Value("${mercadopago.checkout-pro.success-url:}") String successUrl,
            @Value("${mercadopago.checkout-pro.pending-url:}") String pendingUrl,
            @Value("${mercadopago.checkout-pro.failure-url:}") String failureUrl,
            @Value("${mercadopago.checkout-pro.notification-url:}") String notificationUrl,
            @Value("${mercadopago.checkout-pro.sandbox:false}") boolean sandbox,
            @Value("${mercadopago.diagnostic-logging:false}") boolean diagnosticLogging) {
        this(RestClient.builder().baseUrl(baseUrl).build(), accessToken, successUrl, pendingUrl,
                failureUrl, notificationUrl, sandbox, diagnosticLogging);
    }

    MercadoPagoCheckoutProGateway(RestClient restClient, String accessToken, String successUrl,
                                  String pendingUrl, String failureUrl, String notificationUrl,
                                  boolean sandbox) {
        this(restClient, accessToken, successUrl, pendingUrl, failureUrl, notificationUrl,
                sandbox, true);
    }

    MercadoPagoCheckoutProGateway(RestClient restClient, String accessToken, String successUrl,
                                  String pendingUrl, String failureUrl, String notificationUrl,
                                  boolean sandbox, boolean diagnosticLogging) {
        this.restClient = restClient;
        this.accessToken = accessToken;
        this.successUrl = successUrl;
        this.pendingUrl = pendingUrl;
        this.failureUrl = failureUrl;
        this.notificationUrl = notificationUrl;
        this.sandbox = sandbox;
        this.diagnosticLogging = diagnosticLogging;
    }

    @Override
    public PreferenceResult createPreference(PreferenceRequest request) {
        requireConfiguration();
        String externalReference = reference(request.orderId(), request.paymentId());
        List<Map<String, Object>> items = new ArrayList<>();
        for (Item item : request.items()) {
            items.add(Map.of("id", item.id(), "title", item.title(), "quantity", item.quantity(),
                    "currency_id", "BRL", "unit_price", item.unitPrice()));
        }
        if (request.shippingCost().signum() > 0) {
            items.add(Map.of("id", "shipping", "title", request.shippingName(), "quantity", 1,
                    "currency_id", "BRL", "unit_price", request.shippingCost()));
        }
        BigDecimal itemTotal = items.stream().map(item -> ((BigDecimal) item.get("unit_price"))
                        .multiply(BigDecimal.valueOf((Integer) item.get("quantity"))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (itemTotal.compareTo(request.total()) != 0) {
            throw new PaymentProviderException("Itens da preferência não correspondem ao total do pedido");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("payer", Map.of("name", request.payerName(), "email", request.payerEmail()));
        body.put("external_reference", externalReference);
        body.put("back_urls", Map.of("success", successUrl, "pending", pendingUrl,
                "failure", failureUrl));
        body.put("auto_return", "approved");
        if (StringUtils.hasText(notificationUrl)) {
            body.put("notification_url", webhookNotificationUrl(notificationUrl));
        }
        try {
            JsonNode response = restClient.post().uri(CREATE_PREFERENCE)
                    .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + accessToken)
                    .body(body).retrieve().body(JsonNode.class);
            String id = text(response, "id");
            String url = text(response, sandbox ? "sandbox_init_point" : "init_point");
            if (!StringUtils.hasText(id) || !StringUtils.hasText(url)) {
                throw new PaymentProviderException("Resposta inválida ao criar preferência Mercado Pago");
            }
            logPreferenceDiagnostic(response, id, externalReference);
            return new PreferenceResult(id, externalReference, url);
        } catch (RestClientResponseException exception) {
            boolean definitive = exception.getStatusCode().is4xxClientError()
                    && exception.getStatusCode().value() != 408
                    && exception.getStatusCode().value() != 429;
            throw new PaymentProviderException("Falha ao criar preferência Mercado Pago", exception,
                    definitive ? PaymentProviderException.Reason.DEFINITIVE_REJECTION
                            : PaymentProviderException.Reason.GENERIC);
        } catch (RestClientException exception) {
            throw new PaymentProviderException("Falha ao criar preferência Mercado Pago", exception);
        }
    }

    @Override
    public PaymentResult findPayment(String providerPaymentId) {
        if (!StringUtils.hasText(accessToken)) {
            throw new PaymentProviderException("Mercado Pago não está configurado");
        }
        try {
            JsonNode response = restClient.get().uri(FIND_PAYMENT, providerPaymentId)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve().body(JsonNode.class);
            if (response == null) throw new PaymentProviderException("Resposta vazia do Mercado Pago");
            logPaymentDiagnostic(response, providerPaymentId);
            return new PaymentResult(text(response, "id"), text(response, "external_reference"),
                    mapStatus(text(response, "status")), text(response, "status_detail"),
                    decimal(response, "transaction_amount"), text(response, "currency_id"),
                    text(response, "payment_type_id"), text(response, "payment_method_id"),
                    instant(response, "date_approved"));
        } catch (RestClientResponseException exception) {
            throw new PaymentProviderException("Falha ao consultar pagamento Mercado Pago", exception);
        } catch (RestClientException exception) {
            throw new PaymentProviderException("Falha ao consultar pagamento Mercado Pago", exception);
        }
    }

    private void requireConfiguration() {
        if (!StringUtils.hasText(accessToken)) throw new PaymentProviderException("Mercado Pago não está configurado");
        requirePublicUrl(successUrl, "success");
        requirePublicUrl(pendingUrl, "pending");
        requirePublicUrl(failureUrl, "failure");
    }

    private void requirePublicUrl(String value, String name) {
        String lower = value == null ? "" : value.toLowerCase();
        if (!(lower.startsWith("https://") || lower.startsWith("http://"))
                || lower.contains("localhost") || lower.contains("127.0.0.1")) {
            throw new PaymentProviderException("back_url " + name + " do Checkout Pro não é pública");
        }
    }

    private String webhookNotificationUrl(String value) {
        return UriComponentsBuilder.fromUriString(value)
                .replaceQueryParam("source_news", "webhooks")
                .build().toUriString();
    }

    private void logPreferenceDiagnostic(JsonNode response, String preferenceId,
                                         String externalReference) {
        if (!diagnosticLogging) return;
        logger.info("Mercado Pago Checkout Pro preference diagnostic; credentialHint={}; "
                        + "configuredSandbox={}; preferenceId={}; clientId={}; collectorId={}; "
                        + "responseLiveMode={}; checkoutPointField={}; externalReference={}; "
                        + "notificationUrl={}",
                credentialHint(), sandbox, preferenceId, safeText(response, "client_id"),
                safeText(response, "collector_id"), safeText(response, "live_mode"),
                sandbox ? "sandbox_init_point" : "init_point", externalReference,
                StringUtils.hasText(notificationUrl) ? webhookNotificationUrl(notificationUrl)
                        : "<absent>");
    }

    private void logPaymentDiagnostic(JsonNode response, String requestedPaymentId) {
        if (!diagnosticLogging) return;
        logger.info("Mercado Pago authenticated payment diagnostic; requestedPaymentId={}; "
                        + "responsePaymentId={}; applicationId={}; collectorId={}; liveMode={}; "
                        + "externalReference={}; status={}",
                requestedPaymentId, safeText(response, "id"), safeText(response, "application_id"),
                safeText(response, "collector_id"), safeText(response, "live_mode"),
                safeText(response, "external_reference"), safeText(response, "status"));
    }

    private String credentialHint() {
        if (accessToken.startsWith("TEST-")) return "TEST_PREFIX";
        if (accessToken.startsWith("APP_USR-")) return "APP_USR_UNDETERMINED";
        return "UNKNOWN_PREFIX";
    }

    private String safeText(JsonNode node, String field) {
        String value = text(node, field);
        return StringUtils.hasText(value) ? value : "<absent>";
    }

    private PaymentStatus mapStatus(String status) {
        if (status == null) throw new PaymentProviderException("Status ausente no pagamento Mercado Pago");
        return switch (status.toLowerCase()) {
            case "approved" -> PaymentStatus.PAID;
            case "pending", "in_process", "authorized", "in_mediation" -> PaymentStatus.PENDING;
            case "rejected" -> PaymentStatus.FAILED;
            case "cancelled", "canceled" -> PaymentStatus.CANCELED;
            default -> throw new PaymentProviderException("Status não suportado no pagamento Mercado Pago");
        };
    }

    private static String reference(Long orderId, Long paymentId) {
        return "garage_order_" + orderId + "_payment_" + paymentId;
    }

    private String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.decimalValue();
    }

    private Instant instant(JsonNode node, String field) {
        String value = text(node, field);
        if (!StringUtils.hasText(value)) return null;
        try { return Instant.parse(value); }
        catch (DateTimeParseException ignored) { return null; }
    }
}
