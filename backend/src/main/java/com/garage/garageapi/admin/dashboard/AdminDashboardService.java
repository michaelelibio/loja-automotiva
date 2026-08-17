package com.garage.garageapi.admin.dashboard;

import com.garage.garageapi.admin.dashboard.dto.AdminDashboardResponse;
import com.garage.garageapi.order.entity.OrderStatus;
import com.garage.garageapi.order.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminDashboardService {
    private final OrderRepository orderRepository;
    private final Clock clock;
    private final ZoneId businessZone;

    @Autowired
    public AdminDashboardService(OrderRepository orderRepository,
                                 @Value("${app.business.time-zone:America/Sao_Paulo}") String timeZone) {
        this(orderRepository, Clock.systemUTC(), ZoneId.of(timeZone));
    }

    AdminDashboardService(OrderRepository orderRepository, Clock clock, ZoneId businessZone) {
        this.orderRepository = orderRepository;
        this.clock = clock;
        this.businessZone = businessZone;
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse get() {
        LocalDate today = LocalDate.now(clock.withZone(businessZone));
        Instant todayStart = startOf(today);
        Instant tomorrowStart = startOf(today.plusDays(1));
        BigDecimal revenueToday = money(orderRepository.sumConfirmedRevenueInPeriod(
                OrderStatus.confirmedRevenueStatuses(), todayStart, tomorrowStart));
        long paidOrdersToday = orderRepository.countConfirmedInPeriod(
                OrderStatus.confirmedRevenueStatuses(), todayStart, tomorrowStart);
        BigDecimal averageTicket = paidOrdersToday == 0 ? BigDecimal.ZERO.setScale(2)
                : revenueToday.divide(BigDecimal.valueOf(paidOrdersToday), 2, RoundingMode.HALF_UP);

        AdminDashboardResponse.Summary summary = new AdminDashboardResponse.Summary(
                revenueToday,
                orderRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(todayStart, tomorrowStart),
                averageTicket,
                orderRepository.countByStatus(OrderStatus.PENDING_PAYMENT),
                orderRepository.countByStatus(OrderStatus.PROCESSING),
                orderRepository.countByStatus(OrderStatus.SHIPPED));

        List<AdminDashboardResponse.RecentOrder> recent = orderRepository
                .findTop5ByOrderByCreatedAtDescIdDesc().stream().map(order ->
                        new AdminDashboardResponse.RecentOrder(order.getId(),
                                new AdminDashboardResponse.Customer(order.getUser().getId(),
                                        order.getUser().getName(), order.getUser().getEmail()),
                                order.getTotal(), order.getStatus(), order.getCreatedAt())).toList();

        List<AdminDashboardResponse.DailyRevenue> daily = new ArrayList<>(7);
        for (int offset = 6; offset >= 0; offset--) {
            LocalDate date = today.minusDays(offset);
            daily.add(new AdminDashboardResponse.DailyRevenue(date,
                    money(orderRepository.sumConfirmedRevenueInPeriod(OrderStatus.confirmedRevenueStatuses(),
                            startOf(date), startOf(date.plusDays(1))))));
        }

        Map<OrderStatus, Long> counts = orderRepository.countGroupedByStatus().stream()
                .collect(Collectors.toMap(OrderRepository.StatusCount::getStatus,
                        OrderRepository.StatusCount::getQuantity));
        List<AdminDashboardResponse.StatusQuantity> byStatus = List.of(OrderStatus.values()).stream()
                .map(status -> new AdminDashboardResponse.StatusQuantity(status,
                        counts.getOrDefault(status, 0L))).toList();

        return new AdminDashboardResponse(summary, recent, daily, byStatus);
    }

    private Instant startOf(LocalDate date) { return date.atStartOfDay(businessZone).toInstant(); }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
