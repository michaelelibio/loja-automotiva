package com.garage.garageapi.payment.repository;

import com.garage.garageapi.payment.entity.PaymentFinancialEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PaymentFinancialEventRepository extends JpaRepository<PaymentFinancialEvent, Long> {
    List<PaymentFinancialEvent> findAllByPaymentIdOrderByIdAsc(Long paymentId);
}
