package com.garage.garageapi.order.fulfillment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderFulfillmentRepository extends JpaRepository<OrderFulfillment, Long> {
    @Query("""
        select f.order.id from OrderFulfillment f
        where f.provider = 'ALIEXPRESS' and f.status = 'PENDING' and f.attemptCount = 0
          and f.dispatchManualHold = false and f.order.paymentReviewRequired = false
          and f.supplierOrderId is null and f.order.status in ('PAID','PROCESSING')
          and (f.lastError is null or f.dispatchNextCheckAt <= :now)
          and exists (select p.id from Payment p where p.order = f.order and p.method = 'MERCADO_PAGO'
            and p.status = 'PAID' and p.providerLiveMode = true and p.providerVerifiedAt is not null
            and p.paidAt is not null and p.providerPaymentId is not null)
        order by f.updatedAt, f.order.id
        """)
    java.util.List<Long> findAutomaticDispatchCandidates(@Param("now") java.time.Instant now, org.springframework.data.domain.Pageable pageable);
    @Query("select o.paymentReviewRequired from Order o where o.id = :orderId")
    Optional<Boolean> paymentReviewRequired(@Param("orderId") Long orderId);
    Optional<OrderFulfillment> findByOrderId(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from OrderFulfillment f join fetch f.order where f.order.id = :orderId")
    Optional<OrderFulfillment> findByOrderIdForUpdate(@Param("orderId") Long orderId);
}
