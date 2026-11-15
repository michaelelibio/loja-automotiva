package com.garage.garageapi.payment.repository;

import com.garage.garageapi.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import com.garage.garageapi.payment.entity.PaymentStatus;
import com.garage.garageapi.payment.entity.PaymentMethod;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @Query("select p.order.id from Payment p where p.externalReference = :externalReference")
    Optional<Long> findOrderIdByExternalReference(@Param("externalReference") String externalReference);
    interface FinancePaymentMethod {
        PaymentMethod getMethod();
        long getOrders();
        java.math.BigDecimal getRevenue();
    }
    List<Payment> findAllByOrderIdOrderByCreatedAtDescIdDesc(Long orderId);
    Optional<Payment> findFirstByOrderIdOrderByCreatedAtDescIdDesc(Long orderId);
    Optional<Payment> findFirstByOrderIdAndStatusOrderByCreatedAtDescIdDesc(Long orderId,
                                                                            PaymentStatus status);

    @Query("select p from Payment p where p.order.id in :orderIds " +
            "order by p.order.id asc, p.createdAt desc, p.id desc")
    List<Payment> findAllLatestCandidatesByOrderIds(@Param("orderIds") List<Long> orderIds);

    @Query("select p from Payment p where p.order.id in :orderIds and p.status = :status " +
            "order by p.order.id asc, p.createdAt desc, p.id desc")
    List<Payment> findAllByOrderIdsAndStatusNewestFirst(@Param("orderIds") List<Long> orderIds,
                                                        @Param("status") PaymentStatus status);

    @Query("""
            select p.method as method, count(p.order.id) as orders, sum(coalesce(p.order.paymentNetRevenue,p.order.total)) as revenue
              from Payment p
             where (p.status = :paidStatus or p.status in ('PARTIALLY_REFUNDED','REFUNDED','CHARGED_BACK','DISPUTED','CANCELED'))
               and p.order.status in :orderStatuses
               and p.order.createdAt >= :start and p.order.createdAt < :end
               and p.id = coalesce(p.order.financialPaymentId, (select max(p2.id) from Payment p2
                            where p2.order.id = p.order.id and p2.status = :paidStatus))
             group by p.method
             order by p.method asc
            """)
    List<FinancePaymentMethod> aggregateFinanceByPaymentMethod(
            @Param("paidStatus") PaymentStatus paidStatus,
            @Param("orderStatuses") java.util.Set<com.garage.garageapi.order.entity.OrderStatus> orderStatuses,
            @Param("start") java.time.Instant start, @Param("end") java.time.Instant end);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p join fetch p.order where p.externalReference = :externalReference")
    Optional<Payment> findByExternalReferenceForUpdate(
            @Param("externalReference") String externalReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);
}
