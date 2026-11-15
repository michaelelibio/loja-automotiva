package com.garage.garageapi.payment.reconciliation;

import com.garage.garageapi.payment.service.PaymentWebhookService;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class PaymentReconciliationWorker {
    private static final Logger log=LoggerFactory.getLogger(PaymentReconciliationWorker.class);
    private final PaymentReconciliationStore store;
    private final MercadoPagoPaymentSearchClient search;
    private final PaymentWebhookService notifications;
    private final boolean enabled;
    private final int batchSize;
    private final AtomicBoolean running=new AtomicBoolean();
    public PaymentReconciliationWorker(PaymentReconciliationStore store,MercadoPagoPaymentSearchClient search,
            PaymentWebhookService notifications,@Value("${mercadopago.reconciliation.enabled:false}") boolean enabled,
            @Value("${mercadopago.reconciliation.batch-size:20}") int batchSize) {
        this.store=store;this.search=search;this.notifications=notifications;this.enabled=enabled;
        this.batchSize=Math.max(1,Math.min(batchSize,50));
    }
    @Scheduled(fixedDelayString="${mercadopago.reconciliation.poll-ms:60000}",
               initialDelayString="${mercadopago.reconciliation.poll-ms:60000}")
    public void recover() {
        if(!enabled || !running.compareAndSet(false,true))return;
        try {
            for(Long id:store.candidates(Instant.now(),batchSize)) {
                try { reconcile(id); }
                catch(RuntimeException ignored) {log.warn("Payment recovery deferred; paymentId={}; recovery=scheduled",id);}
            }
        } finally {running.set(false);}
    }
    void reconcile(Long id) {
        var claim=store.claim(id,Instant.now());if(claim==null)return;
        try {
            List<String> ids=search.findIds(claim.externalReference());
            if(ids.size()>1 || !ids.isEmpty() && claim.providerPaymentId()!=null && !ids.contains(claim.providerPaymentId())) {
                store.complete(claim,Instant.now(),"AMBIGUOUS_PROVIDER_PAYMENTS",true);return;
            }
            String providerId=claim.providerPaymentId()!=null?claim.providerPaymentId():ids.isEmpty()?null:ids.get(0);
            if(providerId!=null)notifications.processPaymentNotification(providerId);
            store.complete(claim,Instant.now(),null,false);
        } catch(MercadoPagoPaymentSearchClient.AmbiguousPaymentsException ignored) {
            store.complete(claim,Instant.now(),"AMBIGUOUS_PROVIDER_PAYMENTS",true);
        } catch(RuntimeException ignored) {
            store.complete(claim,Instant.now(),"PROVIDER_LOOKUP_FAILED",false);
            log.warn("Payment recovery lookup failed; paymentId={}; recovery=scheduled",id);
        }
    }
}
