package com.garage.garageapi.payment.webhook;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class WebhookDiagnosticPrivacyTests {
    @Test void diagnosticModeDoesNotPrintSignatureOrSecret(CapturedOutput output){
        String secret="synthetic-diagnostic-secret",hash="a".repeat(64),signature="ts=1755172800123,v1="+hash;
        new MercadoPagoWebhookSignatureVerifier(secret,true).verify(signature,"synthetic-request","123");
        assertThat(output.getAll()).doesNotContain(secret,signature,hash,"secretFingerprint","calculatedHmac");
    }
    @Test void oversizedAndDuplicateSignaturePartsAreRejected(){
        var verifier=new MercadoPagoWebhookSignatureVerifier("synthetic-secret",false);
        assertThat(verifier.verify("x".repeat(513),"r","1")).isEqualTo(MercadoPagoWebhookSignatureVerifier.Verification.SIGNATURE_MALFORMED);
        assertThat(verifier.verify("ts=1,ts=2,v1="+"a".repeat(64),"r","1")).isEqualTo(MercadoPagoWebhookSignatureVerifier.Verification.SIGNATURE_MALFORMED);
    }
}
