package com.garage.garageapi.order.repository;

import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Page<Order> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    Page<Order> findAllByStatusOrderByCreatedAtDescIdDesc(OrderStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "items")
    List<Order> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = "items")
    Optional<Order> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id and o.user.id = :userId")
    Optional<Order> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForLifecycleUpdate(@Param("id") Long id);
}
