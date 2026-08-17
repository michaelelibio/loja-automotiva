package com.garage.garageapi.admin.dashboard.dto;

import com.garage.garageapi.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AdminDashboardResponse(
        Summary summary,
        List<RecentOrder> recentOrders,
        List<DailyRevenue> revenueLast7Days,
        List<StatusQuantity> ordersByStatus
) {
    public record Summary(BigDecimal revenueToday, long ordersToday,
                          BigDecimal averageTicketToday, long pendingPayment,
                          long processing, long shipped) { }

    public record RecentOrder(Long orderId, Customer customer, BigDecimal total,
                              OrderStatus status, Instant createdAt) { }

    public record Customer(Long userId, String name, String email) { }

    public record DailyRevenue(LocalDate date, BigDecimal revenue) { }

    public record StatusQuantity(OrderStatus status, long quantity) { }
}
