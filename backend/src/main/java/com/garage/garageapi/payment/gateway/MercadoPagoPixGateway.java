package com.garage.garageapi.payment.gateway;

import com.garage.garageapi.payment.entity.PaymentStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class MercadoPagoPixGateway implements PixPaymentGateway {
    private static final Logger logger = LoggerFactory.getLogger(MercadoPagoPixGateway.class);
    private static final String CREATE_PIX_ENDPOINT = "/v1/orders";
    private static final String FIND_PIX_ENDPOINT = "/v1/orders/{id}";
    private static final String SANDBOX_APPROVED_PAYER_EMAIL = "test_user_br@testuser.com";
    private static final String SANDBOX_APPROVED_PAYER_FIRST_NAME = "APRO";
    private static final Pattern IDEMPOTENCY_KEY_ALREADY_USED = Pattern.compile(
            "\\\"code\\\"\\s*:\\s*\\\"idempotency_key_already_used\\\"");

    private final RestClient restClient;
    private final String accessToken;
    private final String sandboxPayerEmail;
    private final boolean diagnosticLoggingEnabled;

    @Autowired
    public MercadoPagoPixGateway(@Value("${mercadopago.base-url:https://api.mercadopago.com}") String baseUrl,
                                 @Value("${mercadopago.access-token:}") String accessToken,
                                 @Value("${mercadopago.sandbox-payer-email:}") String sandboxPayerEmail,
                                 @Value("${mercadopago.diagnostic-logging:false}") boolean diagnosticLoggingEnabled) {
        this(RestClient.builder().baseUrl(baseUrl).build(), accessToken, sandboxPayerEmail,
                diagnosticLoggingEnabled);
    }

    MercadoPagoPixGateway(RestClient restClient, String accessToken) {
        this(restClient, accessToken, "", false);
    }

    MercadoPagoPixGateway(RestClient restClient, String accessToken, boolean diagnosticLoggingEnabled) {
        this(restClient, accessToken, "", diagnosticLoggingEnabled);
    }

    MercadoPagoPixGateway(RestClient restClient, String accessToken, String sandboxPayerEmail) {
        this(restClient, accessToken, sandboxPayerEmail, false);
    }

    MercadoPagoPixGateway(RestClient restClient, String accessToken, String sandboxPayerEmail,
                           boolean diagnosticLoggingEnabled) {
        this.restClient = restClient;
        this.accessToken = accessToken;
        this.sandboxPayerEmail = sandboxPayerEmail;
        this.diagnosticLoggingEnabled = diagnosticLoggingEnabled;
    }

    @Override
    public Result create(Request request) {
        if (accessToken.isBlank()) {
            throw new PaymentProviderException("Mercado Pago não está configurado");
        }
        Map<String, Object> payment = Map.of(
                "amount", request.amount().toPlainString(),
                "payment_method", Map.of("id", "pix", "type", "bank_transfer"));
        boolean usesSandboxPayerEmail = StringUtils.hasText(sandboxPayerEmail);
        Map<String, String> payer = usesSandboxPayerEmail
                ? Map.of("email", SANDBOX_APPROVED_PAYER_EMAIL,
                        "first_name", SANDBOX_APPROVED_PAYER_FIRST_NAME)
                : Map.of("email", request.payerEmail());
        logger.info("Mercado Pago payer email source={}", usesSandboxPayerEmail ? "sandbox" : "user");
        Map<String, Object> body = Map.of(
                "type", "online",
                "total_amount", request.amount().toPlainString(),
                "external_reference", "garage_order_" + request.orderId() + "_payment_" + request.paymentId(),
                "processing_mode", "automatic",
                "transactions", Map.of("payments", List.of(payment)),
                "payer", payer);
        try {
            JsonNode response = restClient.post().uri(CREATE_PIX_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("X-Idempotency-Key", request.idempotencyKey())
                    .body(body).retrieve().body(JsonNode.class);
            return mapResponse(response, false);
        } catch (RestClientResponseException exception) {
            logProviderError("POST " + CREATE_PIX_ENDPOINT, exception);
            PaymentProviderException.Reason reason = isDefinitiveRejection(exception)
                    ? PaymentProviderException.Reason.DEFINITIVE_REJECTION
                    : PaymentProviderException.Reason.GENERIC;
            throw new PaymentProviderException(
                    "Falha ao criar cobrança PIX no Mercado Pago", exception, reason);
        } catch (RestClientException exception) {
            throw new PaymentProviderException("Falha ao criar cobrança PIX no Mercado Pago", exception);
        }
    }

    @Override
    public Result find(String providerOrderId) {
        if (accessToken.isBlank()) {
            throw new PaymentProviderException("Mercado Pago não está configurado");
        }
        try {
            JsonNode response = restClient.get().uri(FIND_PIX_ENDPOINT, providerOrderId)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve().body(JsonNode.class);
            return mapResponse(response, true);
        } catch (RestClientResponseException exception) {
            logProviderError("GET " + FIND_PIX_ENDPOINT, exception);
            throw new PaymentProviderException(
                    "Falha ao consultar cobrança PIX no Mercado Pago", exception);
        } catch (RestClientException exception) {
            throw new PaymentProviderException("Falha ao consultar cobrança PIX no Mercado Pago", exception);
        }
    }

    private boolean isDefinitiveRejection(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == 409) {
            return IDEMPOTENCY_KEY_ALREADY_USED.matcher(exception.getResponseBodyAsString()).find();
        }
        return status >= 400 && status < 500 && status != 408 && status != 429;
    }

    private void logProviderError(String endpoint, RestClientResponseException exception) {
        if (!diagnosticLoggingEnabled) return;
        logger.error("Diagnóstico Mercado Pago - operação PIX; endpoint={}; httpStatus={}",
                endpoint, exception.getStatusCode().value());
    }

    private Result mapResponse(JsonNode response, boolean authoritativeOrderState) {
        if (response == null) throw new PaymentProviderException("Resposta vazia do Mercado Pago");
        String providerOrderId = text(response, "id");
        if (!StringUtils.hasText(providerOrderId)) {
            throw new PaymentProviderException("Identificador da order ausente na resposta do Mercado Pago");
        }
        JsonNode payments = response.path("transactions").path("payments");
        if (!payments.isArray() || payments.isEmpty()) {
            throw new PaymentProviderException("Resposta inválida do Mercado Pago");
        }
        JsonNode payment = payments.get(0);
        String providerPaymentId = text(payment, "id");
        if (!StringUtils.hasText(providerPaymentId)) {
            throw new PaymentProviderException("Identificador do pagamento ausente na resposta do Mercado Pago");
        }
        JsonNode method = payment.path("payment_method");
        PaymentStatus status = authoritativeOrderState
                ? mapOrderStatus(text(response, "status"), text(response, "status_detail"))
                : mapCreationStatus(text(payment, "status"));
        Instant paidAt = null;
        if (status == PaymentStatus.PAID) {
            paidAt = instant(response, "last_updated_date");
            if (paidAt == null) {
                paidAt = Instant.now();
                logger.warn("Mercado Pago paid order without a valid last_updated_date; "
                        + "using local synchronization time");
            }
        }
        Instant expiresAt = instant(payment, "date_of_expiration");
        if (expiresAt == null) expiresAt = instant(payment, "expiration_time");
        return new Result(providerOrderId, providerPaymentId,
                text(response, "external_reference"), status,
                text(method, "qr_code"), text(method, "qr_code_base64"),
                expiresAt, paidAt);
    }

    private PaymentStatus mapCreationStatus(String status) {
        if (status == null) throw new PaymentProviderException("Status ausente na resposta do Mercado Pago");
        return switch (status.toLowerCase()) {
            case "action_required", "pending", "processing", "created" -> PaymentStatus.PENDING;
            case "processed" -> PaymentStatus.PAID;
            case "expired" -> PaymentStatus.EXPIRED;
            case "canceled", "cancelled" -> PaymentStatus.CANCELED;
            case "failed", "rejected" -> PaymentStatus.FAILED;
            default -> throw new PaymentProviderException("Status desconhecido retornado pelo Mercado Pago");
        };
    }

    private PaymentStatus mapOrderStatus(String status, String statusDetail) {
        if (!StringUtils.hasText(status)) {
            throw new PaymentProviderException("Status da order ausente na resposta do Mercado Pago");
        }
        String normalizedStatus = status.toLowerCase();
        String normalizedDetail = statusDetail == null ? null : statusDetail.toLowerCase();
        return switch (normalizedStatus) {
            case "created", "action_required", "processing", "pending" -> PaymentStatus.PENDING;
            case "processed" -> {
                if (!"accredited".equals(normalizedDetail)) {
                    throw new PaymentProviderException(
                            "Order processada sem confirmaÃ§Ã£o de crÃ©dito pelo Mercado Pago");
                }
                yield PaymentStatus.PAID;
            }
            case "expired" -> PaymentStatus.EXPIRED;
            case "canceled", "cancelled" -> PaymentStatus.CANCELED;
            case "failed", "rejected" -> PaymentStatus.FAILED;
            default -> throw new PaymentProviderException("Status desconhecido retornado pelo Mercado Pago");
        };
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private Instant instant(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) return null;
        try { return Instant.parse(value); }
        catch (DateTimeParseException ignored) { return null; }
    }
}
