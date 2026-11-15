package com.garage.garageapi.payment.reconciliation;

import com.garage.garageapi.payment.entity.PaymentMethod;
import com.garage.garageapi.payment.repository.PaymentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

@Service
public class PaymentReconciliationStore {
    public record Claim(Long paymentId,String providerPaymentId,String externalReference,String token) { }
    public record Failure(Long paymentId,Instant lastCheckedAt,Instant nextCheckAt,int failureCount,
                          String code,boolean reviewRequired) { }
    private final PaymentRepository payments;
    private final PaymentReconciliationRepository schedules;
    public PaymentReconciliationStore(PaymentRepository payments,PaymentReconciliationRepository schedules) {
        this.payments=payments;this.schedules=schedules;
    }
    @Transactional(readOnly=true)
    public List<Long> candidates(Instant now,int batchSize) {
        return schedules.candidates(now,now.minusSeconds(365L*86400),now.minusSeconds(30),PageRequest.of(0,batchSize));
    }
    @Transactional
    public Claim claim(Long id,Instant now) {
        var payment=payments.findByIdForUpdate(id).orElse(null);
        if(payment==null || payment.getMethod()!=PaymentMethod.MERCADO_PAGO || payment.getProviderPreferenceId()==null)return null;
        String expected="garage_order_"+payment.getOrder().getId()+"_payment_"+payment.getId();
        if(!expected.equals(payment.getExternalReference()))return null;
        var row=schedules.findById(id).orElseGet(()->new PaymentReconciliation(id,now));
        String token=row.claim(now);if(token==null)return null;
        schedules.saveAndFlush(row);
        return new Claim(id,payment.getProviderPaymentId(),expected,token);
    }
    @Transactional
    public void complete(Claim claim,Instant now,String code,boolean review) {
        var payment=payments.findByIdForUpdate(claim.paymentId()).orElse(null);
        if(payment==null)return;
        var row=schedules.findById(claim.paymentId()).orElse(null);if(row==null)return;
        long interval="PENDING".equals(payment.getStatus().name())?120:900;
        row.complete(claim.token(),now,interval,code,review);
    }
    @Transactional(readOnly=true)
    public List<Failure> failures() {
        return schedules.findByLastFailureCodeIsNotNullOrderByNextCheckAtAsc(PageRequest.of(0,100)).stream()
                .map(r->new Failure(r.getPaymentId(),r.getLastCheckedAt(),r.getNextCheckAt(),r.getFailureCount(),
                        r.getLastFailureCode(),r.isReviewRequired())).toList();
    }
}
