package com.garage.garageapi.order.repository;

import com.garage.garageapi.order.entity.OrderItem;
import com.garage.garageapi.order.entity.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    interface ProductSales {
        Long getProductId();
        String getName();
        long getQuantitySold();
        BigDecimal getRevenue();
    }

    interface OrderCost {
        Long getOrderId();
        BigDecimal getKnownProductCost();
        long getUnknownItems();
    }

    @Query("select coalesce(sum(i.unitCost * i.quantity), 0) from OrderItem i " +
            "where i.unitCost is not null and i.order.status in :statuses " +
            "and i.order.createdAt >= :start and i.order.createdAt < :end")
    BigDecimal sumKnownCostInPeriod(@Param("statuses") Set<OrderStatus> statuses,
                                    @Param("start") Instant start, @Param("end") Instant end);

    @Query("select count(distinct i.order.id) from OrderItem i where i.unitCost is null " +
            "and i.order.status in :statuses and i.order.createdAt >= :start " +
            "and i.order.createdAt < :end")
    long countOrdersWithUnknownCostInPeriod(@Param("statuses") Set<OrderStatus> statuses,
                                            @Param("start") Instant start,
                                            @Param("end") Instant end);

    @Query("""
            select i.productId as productId, max(i.productName) as name,
                   sum(i.quantity) as quantitySold, sum(i.subtotal) as revenue
              from OrderItem i
             where i.order.status in :statuses
               and i.order.createdAt >= :start and i.order.createdAt < :end
             group by i.productId
             order by sum(i.quantity) desc, i.productId asc
            """)
    List<ProductSales> findTopSelling(@Param("statuses") Set<OrderStatus> statuses,
                                      @Param("start") Instant start, @Param("end") Instant end,
                                      Pageable pageable);

    @Query("""
            select i.productId as productId, max(i.productName) as name,
                   sum(i.quantity) as quantitySold, sum(i.subtotal) as revenue
              from OrderItem i
             where i.order.status in :statuses
               and i.order.createdAt >= :start and i.order.createdAt < :end
             group by i.productId
            having sum(i.quantity) > 0
             order by sum(i.quantity) asc, i.productId asc
            """)
    List<ProductSales> findLowestSelling(@Param("statuses") Set<OrderStatus> statuses,
                                         @Param("start") Instant start, @Param("end") Instant end,
                                         Pageable pageable);

    @Query("""
            select i.order.id as orderId,
                   coalesce(sum(i.unitCost * i.quantity), 0) as knownProductCost,
                   sum(case when i.unitCost is null then 1 else 0 end) as unknownItems
              from OrderItem i
             where i.order.id in :orderIds
             group by i.order.id
            """)
    List<OrderCost> aggregateCostsByOrderIds(@Param("orderIds") List<Long> orderIds);
}
