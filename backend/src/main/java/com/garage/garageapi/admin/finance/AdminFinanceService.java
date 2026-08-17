package com.garage.garageapi.admin.finance;

import com.garage.garageapi.admin.finance.dto.AdminFinanceResponse;
import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.entity.OrderStatus;
import com.garage.garageapi.order.repository.OrderItemRepository;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.payment.entity.Payment;
import com.garage.garageapi.payment.entity.PaymentStatus;
import com.garage.garageapi.payment.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminFinanceService {
    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");
    private static final int MAX_PERIOD_DAYS = 366;
    private static final int RANKING_LIMIT = 10;
    private static final int RECENT_LIMIT = 10;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final ZoneId businessZone;

    public AdminFinanceService(OrderRepository orderRepository,
                               OrderItemRepository orderItemRepository,
                               PaymentRepository paymentRepository,
                               @Value("${app.business.time-zone:America/Sao_Paulo}") String timeZone) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.businessZone = ZoneId.of(timeZone);
    }

    @Transactional(readOnly = true)
    public AdminFinanceResponse get(LocalDate dateFrom, LocalDate dateTo) {
        validate(dateFrom, dateTo);
        Instant start = startOf(dateFrom);
        Instant endExclusive = startOf(dateTo.plusDays(1));
        Set<OrderStatus> confirmed = OrderStatus.confirmedRevenueStatuses();

        long confirmedOrders = orderRepository.countConfirmedInPeriod(confirmed, start, endExclusive);
        BigDecimal revenue = money(orderRepository.sumConfirmedRevenueInPeriod(confirmed, start, endExclusive));
        BigDecimal knownCost = money(orderItemRepository.sumKnownCostInPeriod(confirmed, start, endExclusive));
        long unknownOrders = orderItemRepository.countOrdersWithUnknownCostInPeriod(
                confirmed, start, endExclusive);
        BigDecimal grossProfit = money(revenue.subtract(knownCost));
        BigDecimal averageTicket = confirmedOrders == 0 ? ZERO_MONEY
                : revenue.divide(BigDecimal.valueOf(confirmedOrders), 2, RoundingMode.HALF_UP);
        BigDecimal grossMargin = revenue.signum() == 0 ? ZERO_MONEY
                : grossProfit.multiply(BigDecimal.valueOf(100))
                .divide(revenue, 2, RoundingMode.HALF_UP);

        return new AdminFinanceResponse(
                new AdminFinanceResponse.Period(dateFrom, dateTo),
                new AdminFinanceResponse.Summary(revenue, confirmedOrders, averageTicket, knownCost,
                        grossProfit, grossMargin),
                coverage(unknownOrders),
                daily(dateFrom, dateTo, confirmed),
                paymentMethods(start, endExclusive, confirmed),
                statusCounts(start, endExclusive),
                productSales(orderItemRepository.findTopSelling(confirmed, start, endExclusive,
                        PageRequest.of(0, RANKING_LIMIT))),
                productSales(orderItemRepository.findLowestSelling(confirmed, start, endExclusive,
                        PageRequest.of(0, RANKING_LIMIT))),
                recent(start, endExclusive, confirmed));
    }

    private List<AdminFinanceResponse.Daily> daily(LocalDate dateFrom, LocalDate dateTo,
                                                    Set<OrderStatus> confirmed) {
        List<AdminFinanceResponse.Daily> result = new ArrayList<>();
        for (LocalDate date = dateFrom; !date.isAfter(dateTo); date = date.plusDays(1)) {
            Instant start = startOf(date);
            Instant end = startOf(date.plusDays(1));
            BigDecimal revenue = money(orderRepository.sumConfirmedRevenueInPeriod(confirmed, start, end));
            BigDecimal cost = money(orderItemRepository.sumKnownCostInPeriod(confirmed, start, end));
            long unknown = orderItemRepository.countOrdersWithUnknownCostInPeriod(confirmed, start, end);
            result.add(new AdminFinanceResponse.Daily(date, revenue, cost,
                    money(revenue.subtract(cost)), coverage(unknown)));
        }
        return result;
    }

    private List<AdminFinanceResponse.PaymentMethodSummary> paymentMethods(
            Instant start, Instant end, Set<OrderStatus> confirmed) {
        return paymentRepository.aggregateFinanceByPaymentMethod(PaymentStatus.PAID, confirmed, start, end)
                .stream().map(value -> new AdminFinanceResponse.PaymentMethodSummary(value.getMethod(),
                        value.getOrders(), money(value.getRevenue()))).toList();
    }

    private List<AdminFinanceResponse.StatusQuantity> statusCounts(Instant start, Instant end) {
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        orderRepository.countGroupedByStatusInPeriod(start, end)
                .forEach(value -> counts.put(value.getStatus(), value.getQuantity()));
        return List.of(OrderStatus.values()).stream()
                .map(status -> new AdminFinanceResponse.StatusQuantity(status,
                        counts.getOrDefault(status, 0L))).toList();
    }

    private List<AdminFinanceResponse.ProductSales> productSales(
            List<OrderItemRepository.ProductSales> values) {
        return values.stream().map(value -> new AdminFinanceResponse.ProductSales(value.getProductId(),
                value.getName(), value.getQuantitySold(), money(value.getRevenue()))).toList();
    }

    private List<AdminFinanceResponse.RecentTransaction> recent(
            Instant start, Instant end, Set<OrderStatus> confirmed) {
        List<Order> orders = orderRepository.findRecentConfirmedInPeriod(confirmed, start, end,
                PageRequest.of(0, RECENT_LIMIT));
        if (orders.isEmpty()) return List.of();
        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        Map<Long, OrderItemRepository.OrderCost> costs = orderItemRepository
                .aggregateCostsByOrderIds(orderIds).stream().collect(Collectors.toMap(
                        OrderItemRepository.OrderCost::getOrderId, Function.identity()));
        Map<Long, Payment> payments = latestPaidPayments(orderIds);
        return orders.stream().map(order -> {
            OrderItemRepository.OrderCost cost = costs.get(order.getId());
            BigDecimal knownCost = money(cost == null ? null : cost.getKnownProductCost());
            boolean costComplete = cost == null || cost.getUnknownItems() == 0;
            Payment payment = payments.get(order.getId());
            return new AdminFinanceResponse.RecentTransaction(order.getId(),
                    new AdminFinanceResponse.Customer(order.getUser().getId(), order.getUser().getName(),
                            order.getUser().getEmail()), order.getStatus(),
                    payment == null ? null : payment.getMethod(),
                    payment == null ? null : payment.getStatus(), order.getTotal(), knownCost,
                    money(order.getTotal().subtract(knownCost)), costComplete, order.getCreatedAt(),
                    payment == null ? null : payment.getPaidAt());
        }).toList();
    }

    private Map<Long, Payment> latestPaidPayments(List<Long> orderIds) {
        if (orderIds.isEmpty()) return Collections.emptyMap();
        Map<Long, Payment> result = new LinkedHashMap<>();
        paymentRepository.findAllByOrderIdsAndStatusNewestFirst(orderIds, PaymentStatus.PAID)
                .forEach(payment -> result.putIfAbsent(payment.getOrder().getId(), payment));
        return result;
    }

    private AdminFinanceResponse.CostCoverage coverage(long unknownOrders) {
        return new AdminFinanceResponse.CostCoverage(unknownOrders == 0, unknownOrders);
    }

    private void validate(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom.isAfter(dateTo)) {
            throw new InvalidFinancePeriodException("dateFrom deve ser anterior ou igual a dateTo");
        }
        long days = ChronoUnit.DAYS.between(dateFrom, dateTo) + 1;
        if (days > MAX_PERIOD_DAYS) {
            throw new InvalidFinancePeriodException("O período financeiro deve possuir no máximo 366 dias");
        }
    }

    private Instant startOf(LocalDate date) { return date.atStartOfDay(businessZone).toInstant(); }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
