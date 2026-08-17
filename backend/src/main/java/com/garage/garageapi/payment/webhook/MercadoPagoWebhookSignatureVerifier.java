package com.garage.garageapi.payment.webhook;

import com.mercadopago.exceptions.MPInvalidWebhookSignatureException;
import com.mercadopago.webhook.WebhookSignatureValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

@Component
public class MercadoPagoWebhookSignatureVerifier {
    private static final Logger logger = LoggerFactory.getLogger(MercadoPagoWebhookSignatureVerifier.class);
    private final String secret;
    private final boolean diagnosticLogging;

    @Autowired
    public MercadoPagoWebhookSignatureVerifier(
            @Value("${mercadopago.webhook-secret:}") String secret,
            @Value("${mercadopago.diagnostic-logging:false}") boolean diagnosticLogging) {
        this.secret = secret;
        this.diagnosticLogging = diagnosticLogging;
        logger.info("Mercado Pago webhook signature validation configured={}", !this.secret.isEmpty());
    }

    MercadoPagoWebhookSignatureVerifier(String secret) {
        this(secret, true);
    }

    public Verification verify(String signature, String requestId, String dataId) {
        if (secret.isEmpty()) return diagnosed(Verification.SECRET_NOT_CONFIGURED,
                dataId, requestId, null, 0);
        if (!StringUtils.hasText(signature)) return diagnosed(Verification.SIGNATURE_MISSING,
                dataId, requestId, null, 0);

        String timestamp = part(signature, "ts");
        String suppliedHash = part(signature, "v1");
        int manifestLength = manifestLength(dataId, requestId, timestamp);
        logger.info("Mercado Pago x-signature parsed; tsPresent={}; tsLength={}; "
                        + "v1Present={}; v1Length={}",
                StringUtils.hasText(timestamp), length(timestamp),
                StringUtils.hasText(suppliedHash), length(suppliedHash));
        if (!StringUtils.hasText(suppliedHash)) {
            return diagnosed(Verification.SIGNATURE_MALFORMED, dataId, requestId, timestamp,
                    manifestLength);
        }

        Verification result;
        try {
            WebhookSignatureValidator.validate(signature, requestId, dataId, secret);
            result = Verification.VALID;
        } catch (MPInvalidWebhookSignatureException ignored) {
            result = Verification.SIGNATURE_INVALID;
        }
        logHmacComparison(signature, dataId, requestId, timestamp, suppliedHash, result);
        return diagnosed(result, dataId, requestId, timestamp, manifestLength);
    }

    private void logHmacComparison(String signature, String dataId, String requestId,
                                   String timestamp, String suppliedHash, Verification sdkResult) {
        if (!diagnosticLogging) return;
        try {
            String manifest = manifest(dataId, requestId, timestamp);
            String calculatedHmac = hmacSha256(manifest, secret);
            logger.info("Mercado Pago webhook HMAC comparison diagnostic; dataId={}; "
                            + "requestId={}; xSignature={}; ts={}; receivedV1={}; "
                            + "secretLength={}; secretFingerprint={}; manifest={}; "
                            + "manifestLength={}; calculatedHmac={}; manualMatch={}; sdkResult={}",
                    dataId, requestId, signature, timestamp, suppliedHash, secret.length(),
                    sha256Fingerprint(secret), manifest,
                    manifest.getBytes(StandardCharsets.UTF_8).length, calculatedHmac,
                    calculatedHmac.equalsIgnoreCase(suppliedHash), sdkResult);
        } catch (GeneralSecurityException exception) {
            logger.warn("Mercado Pago webhook HMAC comparison diagnostic unavailable; sdkResult={}",
                    sdkResult);
        }
    }

    private String manifest(String dataId, String requestId, String timestamp) {
        StringBuilder manifest = new StringBuilder();
        if (StringUtils.hasText(dataId)) manifest.append("id:").append(dataId).append(';');
        if (StringUtils.hasText(requestId)) {
            manifest.append("request-id:").append(requestId).append(';');
        }
        if (StringUtils.hasText(timestamp)) manifest.append("ts:").append(timestamp).append(';');
        return manifest.toString();
    }

    private String hmacSha256(String manifest, String key) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
    }

    private String sha256Fingerprint(String value) throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest, 0, 4);
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

    private int manifestLength(String dataId, String requestId, String timestamp) {
        int length = "ts:;".length() + length(timestamp);
        if (StringUtils.hasText(dataId)) length += "id:;".length() + dataId.trim().length();
        if (StringUtils.hasText(requestId)) {
            length += "request-id:;".length() + requestId.trim().length();
        }
        return length;
    }

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
