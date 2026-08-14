package com.garage.garageapi.payment.controller;

import com.garage.garageapi.payment.service.PaymentWebhookService;
import com.garage.garageapi.payment.webhook.MercadoPagoWebhookSignatureVerifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
public class MercadoPagoWebhookController {
    private static final Logger logger = LoggerFactory.getLogger(MercadoPagoWebhookController.class);
    private final MercadoPagoWebhookSignatureVerifier signatureVerifier;
    private final PaymentWebhookService webhookService;

    public MercadoPagoWebhookController(MercadoPagoWebhookSignatureVerifier signatureVerifier,
                                        PaymentWebhookService webhookService) {
        this.signatureVerifier = signatureVerifier;
        this.webhookService = webhookService;
    }

    @PostMapping("/api/webhooks/mercadopago")
    public ResponseEntity<Void> receive(
            @RequestHeader(name = "x-signature", required = false) String signature,
            @RequestHeader(name = "x-request-id", required = false) String requestId,
            @RequestParam(name = "data.id", required = false) String dataId,
            @RequestParam(name = "type", required = false) String type) {
        logger.info("Mercado Pago webhook received; dataIdSource=query-param; dataIdPresent={}",
                dataId != null && !dataId.isBlank());
        MercadoPagoWebhookSignatureVerifier.Verification verification =
                signatureVerifier.verify(signature, requestId, dataId);
        if (verification != MercadoPagoWebhookSignatureVerifier.Verification.VALID) {
            logger.warn("Mercado Pago webhook rejected; signatureResult={}", verification);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        logger.info("Mercado Pago webhook accepted by signature validation; type={}", type);
        if ("order".equals(type)) webhookService.processOrderNotification(dataId);
        return ResponseEntity.ok().build();
    }
}
