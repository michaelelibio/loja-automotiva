package com.garage.garageapi.order.fulfillment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderFulfillmentRepository extends JpaRepository<OrderFulfillment, Long> {
    Optional<OrderFulfillment> findByOrderId(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from OrderFulfillment f join fetch f.order where f.order.id = :orderId")
    Optional<OrderFulfillment> findByOrderIdForUpdate(@Param("orderId") Long orderId);
}
