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

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findAllByOrderIdOrderByCreatedAtDescIdDesc(Long orderId);
    Optional<Payment> findFirstByOrderIdOrderByCreatedAtDescIdDesc(Long orderId);
    Optional<Payment> findFirstByOrderIdAndStatusOrderByCreatedAtDescIdDesc(Long orderId,
                                                                            PaymentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p join fetch p.order where p.providerOrderId = :providerOrderId")
    Optional<Payment> findByProviderOrderIdForUpdate(@Param("providerOrderId") String providerOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);
}
