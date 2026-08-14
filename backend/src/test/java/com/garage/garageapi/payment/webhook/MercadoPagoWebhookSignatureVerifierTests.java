package com.garage.garageapi.payment.webhook;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class MercadoPagoWebhookSignatureVerifierTests {
    private static final String SECRET = "official-documentation-fixture-secret";

    @Test
    void acceptsOfficialOrdersVectorPreservingAlphanumericDataIdCase() {
        String dataIdFromQueryParameter = "ORDTST01M00BY7XKN1G0C00A0YMS33DA";
        String requestIdFromHeader = "request-id-from-mercado-pago";
        String timestampFromSignature = "1755172800123";
        String expectedV1FromIndependentVector =
                "dae0dc868963a4281c5074d29fd3ad4fff51b851501437eb93d5bd84b36b17ef";
        String signature = "ts=" + timestampFromSignature
                + ",v1=" + expectedV1FromIndependentVector;

        MercadoPagoWebhookSignatureVerifier verifier =
                new MercadoPagoWebhookSignatureVerifier(SECRET);

        assertThat(verifier.verify(signature, requestIdFromHeader, dataIdFromQueryParameter))
                .isEqualTo(MercadoPagoWebhookSignatureVerifier.Verification.VALID);
    }

    @Test
    void omitsMissingManifestComponentsLikeOfficialValidator() throws Exception {
        String timestamp = "1755172800123";
        String manifest = "ts:" + timestamp + ";";
        String signature = "ts=" + timestamp + ",v1=" + hmac(manifest);

        MercadoPagoWebhookSignatureVerifier verifier =
                new MercadoPagoWebhookSignatureVerifier(SECRET);

        assertThat(verifier.verify(signature, null, null))
                .isEqualTo(MercadoPagoWebhookSignatureVerifier.Verification.VALID);
    }

    private String hmac(String manifest) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
    }
}
