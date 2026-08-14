package com.garage.garageapi.payment.webhook;

import com.mercadopago.exceptions.MPInvalidWebhookSignatureException;
import com.mercadopago.webhook.WebhookSignatureValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MercadoPagoWebhookSignatureVerifier {
    private static final Logger logger = LoggerFactory.getLogger(MercadoPagoWebhookSignatureVerifier.class);
    private final String secret;

    @Autowired
    public MercadoPagoWebhookSignatureVerifier(
            @Value("${mercadopago.webhook-secret:}") String secret) {
        this.secret = secret;
        logger.info("Mercado Pago webhook signature validation configured={}", !this.secret.isEmpty());
    }

    public Verification verify(String signature, String requestId, String dataId) {
        if (secret.isEmpty()) return diagnosed(Verification.SECRET_NOT_CONFIGURED,
                dataId, requestId, null, 0);
        if (!StringUtils.hasText(signature)) return diagnosed(Verification.SIGNATURE_MISSING,
                dataId, requestId, null, 0);

        String timestamp = part(signature, "ts");
        String suppliedHash = part(signature, "v1");
        logger.info("Mercado Pago x-signature parsed; tsPresent={}; tsLength={}; "
                        + "v1Present={}; v1Length={}",
                StringUtils.hasText(timestamp), length(timestamp),
                StringUtils.hasText(suppliedHash), length(suppliedHash));
        if (!StringUtils.hasText(suppliedHash)) {
            return diagnosed(Verification.SIGNATURE_MALFORMED, dataId, requestId, timestamp, 0);
        }

        try {
            WebhookSignatureValidator.validate(signature, requestId, dataId, secret);
            return diagnosed(Verification.VALID, dataId, requestId, timestamp, 0);
        } catch (MPInvalidWebhookSignatureException ignored) {
            return diagnosed(Verification.SIGNATURE_INVALID, dataId, requestId, timestamp, 0);
        }
    }

    private Verification diagnosed(Verification result, String dataId, String requestId,
                                    String timestamp, int manifestLength) {
        logger.info("Mercado Pago webhook signature diagnostic; dataIdPresent={}; dataIdLength={}; "
                        + "requestIdPresent={}; requestIdLength={}; tsPresent={}; tsLength={}; "
                        + "manifestLength={}; result={}",
                StringUtils.hasText(dataId), length(dataId), StringUtils.hasText(requestId),
                length(requestId), StringUtils.hasText(timestamp), length(timestamp),
                manifestLength, result);
        return result;
    }

    private int length(String value) { return value == null ? 0 : value.length(); }

    private String part(String signature, String expectedName) {
        for (String part : signature.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2 && pair[0].equals(expectedName)) return pair[1].trim();
        }
        return null;
    }

    public enum Verification {
        VALID, SECRET_NOT_CONFIGURED, SIGNATURE_MISSING, SIGNATURE_MALFORMED, SIGNATURE_INVALID
    }
}
