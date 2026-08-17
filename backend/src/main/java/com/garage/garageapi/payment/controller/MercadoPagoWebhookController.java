package com.garage.garageapi.payment.controller;

import com.garage.garageapi.payment.service.PaymentWebhookService;
import com.garage.garageapi.payment.webhook.MercadoPagoWebhookSignatureVerifier;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import tools.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

@RestController
public class MercadoPagoWebhookController {
    private static final Logger logger = LoggerFactory.getLogger(MercadoPagoWebhookController.class);
    private final MercadoPagoWebhookSignatureVerifier signatureVerifier;
    private final PaymentWebhookService webhookService;
    private final boolean diagnosticLogging;

    public MercadoPagoWebhookController(MercadoPagoWebhookSignatureVerifier signatureVerifier,
                                        PaymentWebhookService webhookService,
                                        @Value("${mercadopago.diagnostic-logging:false}")
                                        boolean diagnosticLogging) {
        this.signatureVerifier = signatureVerifier;
        this.webhookService = webhookService;
        this.diagnosticLogging = diagnosticLogging;
    }

    @PostMapping("/api/webhooks/mercadopago")
    public ResponseEntity<Void> receive(
            @RequestHeader(name = "x-signature", required = false) String signature,
            @RequestHeader(name = "x-request-id", required = false) String requestId,
            @RequestParam(name = "data.id", required = false) String dataId,
            @RequestParam(name = "type", required = false) String type,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        logSafeRequestDiagnostic(request, body);
        String queryDataId = dataId;
        String bodyDataId = bodyText(body, "data", "id");
        if (hasText(queryDataId) && hasText(bodyDataId) && !queryDataId.equals(bodyDataId)) {
            logger.warn("Mercado Pago webhook rejected; conflictingDataId=true");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        dataId = hasText(queryDataId) ? queryDataId : bodyDataId;
        if ((type == null || type.isBlank()) && body != null) {
            JsonNode bodyType = body.path("type");
            if (!bodyType.isMissingNode() && !bodyType.isNull()) type = bodyType.asText();
        }
        String dataIdSource = hasText(queryDataId) ? "query-param"
                : hasText(bodyDataId) ? "json-body" : "absent";
        logger.info("Mercado Pago webhook received; dataIdSource={}; dataIdPresent={}",
                dataIdSource, hasText(dataId));
        MercadoPagoWebhookSignatureVerifier.Verification verification =
                signatureVerifier.verify(signature, requestId, dataId);
        if (verification != MercadoPagoWebhookSignatureVerifier.Verification.VALID) {
            logger.warn("Mercado Pago webhook rejected; signatureResult={}", verification);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        logger.info("Mercado Pago webhook accepted by signature validation; type={}", type);
        if ("payment".equals(type)) {
            if (!hasText(dataId)) {
                logger.warn("Mercado Pago payment webhook rejected; dataIdPresent=false");
                return ResponseEntity.badRequest().build();
            }
            webhookService.processPaymentNotification(dataId);
        }
        return ResponseEntity.ok().build();
    }

    private String bodyText(JsonNode body, String parent, String field) {
        if (body == null) return null;
        JsonNode value = body.path(parent).path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void logSafeRequestDiagnostic(HttpServletRequest request, JsonNode body) {
        if (!diagnosticLogging) return;
        List<String> queryParameterNames = new ArrayList<>();
        request.getParameterNames().asIterator().forEachRemaining(queryParameterNames::add);
        Collections.sort(queryParameterNames);
        JsonNode data = body == null ? null : body.path("data");
        logger.info("Mercado Pago webhook safe request diagnostic; method={}; uri={}; "
                        + "queryParameterNames={}; queryType={}; queryTopic={}; contentType={}; "
                        + "contentLength={}; parsedBodyLength={}; rootKeys={}; dataPresent={}; "
                        + "dataKeys={}; queryDataIdPresent={}; bodyDataIdPresent={}; "
                        + "rootIdPresent={}; resourcePresent={}; topicPresent={}; "
                        + "bodyType={}; bodyTopic={}; bodyAction={}; signaturePresent={}; "
                        + "requestIdPresent={}; requestIdFingerprint={}; webhookUserId={}; "
                        + "webhookLiveMode={}; queryDataId={}",
                request.getMethod(), request.getRequestURI(), queryParameterNames,
                safeParameter(request, "type"), safeParameter(request, "topic"),
                request.getContentType(), request.getContentLengthLong(), parsedLength(body),
                keys(body), present(data), keys(data), hasText(request.getParameter("data.id")),
                present(data == null ? null : data.path("id")),
                present(body == null ? null : body.path("id")),
                present(body == null ? null : body.path("resource")),
                present(body == null ? null : body.path("topic")),
                safeBodyValue(body, "type"), safeBodyValue(body, "topic"),
                safeBodyValue(body, "action"), hasText(request.getHeader("x-signature")),
                hasText(request.getHeader("x-request-id")),
                fingerprint(request.getHeader("x-request-id")), safeBodyValue(body, "user_id"),
                safeBodyValue(body, "live_mode"), safeParameter(request, "data.id"));
    }

    private String safeParameter(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return hasText(value) ? value : "<absent>";
    }

    private String safeBodyValue(JsonNode body, String name) {
        if (body == null) return "<absent>";
        JsonNode value = body.path(name);
        return value.isValueNode() && !value.isNull() ? value.asText() : "<absent>";
    }

    private List<String> keys(JsonNode node) {
        if (node == null || !node.isObject()) return List.of();
        return node.propertyNames().stream().sorted().toList();
    }

    private boolean present(JsonNode node) {
        return node != null && !node.isMissingNode() && !node.isNull();
    }

    private int parsedLength(JsonNode body) {
        return body == null ? 0 : body.toString().getBytes(StandardCharsets.UTF_8).length;
    }

    private String fingerprint(String value) {
        if (!hasText(value)) return "<absent>";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }
}
