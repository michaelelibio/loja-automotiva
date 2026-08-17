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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

public interface OrderRepository extends JpaRepository<Order, Long> {
    interface StatusCount {
        OrderStatus getStatus();
        long getQuantity();
    }

    interface CustomerOrderMetrics {
        Long getUserId();
        long getTotalOrders();
        Instant getLastOrderAt();
    }

    interface CustomerConfirmedMetrics {
        Long getUserId();
        long getConfirmedOrders();
        BigDecimal getTotalSpent();
    }

    interface FinanceStatusCount {
        OrderStatus getStatus();
        long getQuantity();
    }

    Page<Order> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    Page<Order> findAllByStatusOrderByCreatedAtDescIdDesc(OrderStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    List<Order> findTop5ByOrderByCreatedAtDescIdDesc();

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant start, Instant end);

    long countByStatus(OrderStatus status);

    @Query("select count(o) from Order o where o.status in :statuses and o.createdAt >= :start and o.createdAt < :end")
    long countConfirmedInPeriod(@Param("statuses") Set<OrderStatus> statuses,
                                @Param("start") Instant start, @Param("end") Instant end);

    @Query("select coalesce(sum(o.total), 0) from Order o where o.status in :statuses and o.createdAt >= :start and o.createdAt < :end")
    BigDecimal sumConfirmedRevenueInPeriod(@Param("statuses") Set<OrderStatus> statuses,
                                           @Param("start") Instant start, @Param("end") Instant end);

    @Query("select o.status as status, count(o) as quantity from Order o group by o.status")
    List<StatusCount> countGroupedByStatus();

    @EntityGraph(attributePaths = "items")
    List<Order> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    Page<Order> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    @Query("""
            select o.user.id as userId,
                   count(o) as totalOrders,
                   max(o.createdAt) as lastOrderAt
              from Order o
             where o.user.id in :userIds
             group by o.user.id
            """)
    List<CustomerOrderMetrics> aggregateCustomerOrderMetrics(@Param("userIds") List<Long> userIds);

    @Query("""
            select o.user.id as userId,
                   count(o) as confirmedOrders,
                   coalesce(sum(o.total), 0) as totalSpent
              from Order o
             where o.user.id in :userIds
               and o.status in :confirmedStatuses
             group by o.user.id
            """)
    List<CustomerConfirmedMetrics> aggregateCustomerConfirmedMetrics(
            @Param("userIds") List<Long> userIds,
            @Param("confirmedStatuses") Set<OrderStatus> confirmedStatuses);

    @Query("select o.status as status, count(o) as quantity from Order o " +
            "where o.createdAt >= :start and o.createdAt < :end group by o.status")
    List<FinanceStatusCount> countGroupedByStatusInPeriod(@Param("start") Instant start,
                                                          @Param("end") Instant end);

    @EntityGraph(attributePaths = "user")
    @Query("select o from Order o where o.status in :statuses and o.createdAt >= :start " +
            "and o.createdAt < :end order by o.createdAt desc, o.id desc")
    List<Order> findRecentConfirmedInPeriod(@Param("statuses") Set<OrderStatus> statuses,
                                            @Param("start") Instant start,
                                            @Param("end") Instant end,
                                            Pageable pageable);

    @EntityGraph(attributePaths = "items")
    Optional<Order> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id and o.user.id = :userId")
    Optional<Order> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForLifecycleUpdate(@Param("id") Long id);
}
