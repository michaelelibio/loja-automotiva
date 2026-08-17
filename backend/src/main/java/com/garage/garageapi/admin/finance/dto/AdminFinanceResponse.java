package com.garage.garageapi.admin.finance.dto;

import com.garage.garageapi.order.entity.OrderStatus;
import com.garage.garageapi.payment.entity.PaymentMethod;
import com.garage.garageapi.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AdminFinanceResponse(
        Period period,
        Summary summary,
        CostCoverage costCoverage,
        List<Daily> daily,
        List<PaymentMethodSummary> paymentMethods,
        List<StatusQuantity> ordersByStatus,
        List<ProductSales> topSellingProducts,
        List<ProductSales> lowestSellingProducts,
        List<RecentTransaction> recentTransactions
) {
    public record Period(LocalDate dateFrom, LocalDate dateTo) { }

    public record Summary(BigDecimal revenue, long confirmedOrders, BigDecimal averageTicket,
                          BigDecimal knownProductCost, BigDecimal grossProfit,
                          BigDecimal grossMargin) { }

    public record CostCoverage(boolean complete, long ordersWithUnknownCost) { }

    public record Daily(LocalDate date, BigDecimal revenue, BigDecimal knownProductCost,
                        BigDecimal grossProfit, CostCoverage costCoverage) { }

    public record PaymentMethodSummary(PaymentMethod method, long orders, BigDecimal revenue) { }

    public record StatusQuantity(OrderStatus status, long quantity) { }

    public record ProductSales(Long productId, String name, long quantitySold,
                               BigDecimal revenue) { }

    public record Customer(Long userId, String name, String email) { }

    public record RecentTransaction(Long orderId, Customer customer, OrderStatus status,
                                    PaymentMethod paymentMethod, PaymentStatus paymentStatus,
                                    BigDecimal total, BigDecimal knownProductCost,
                                    BigDecimal grossProfit, boolean costComplete,
                                    Instant createdAt, Instant paidAt) { }
}
