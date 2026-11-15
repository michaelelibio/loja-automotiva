package com.garage.garageapi.payment.reconciliation;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface PaymentReconciliationRepository extends JpaRepository<PaymentReconciliation,Long> {
    @Query("""
        select p.id from Payment p left join PaymentReconciliation r on r.paymentId=p.id
         where p.method=com.garage.garageapi.payment.entity.PaymentMethod.MERCADO_PAGO
           and p.externalReference is not null and p.providerPreferenceId is not null
           and p.createdAt>=:oldest and p.createdAt<=:newest
           and (r.paymentId is null or r.reviewRequired=false and r.nextCheckAt<=:now)
         order by coalesce(r.nextCheckAt,p.createdAt),p.id
        """)
    List<Long> candidates(@Param("now") Instant now,@Param("oldest") Instant oldest,
                          @Param("newest") Instant newest,Pageable limit);
    List<PaymentReconciliation> findByLastFailureCodeIsNotNullOrderByNextCheckAtAsc(Pageable limit);
}
