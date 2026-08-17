package com.garage.garageapi.admin.customer;

import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.address.repository.AddressRepository;
import com.garage.garageapi.admin.customer.dto.AdminCustomerDetailResponse;
import com.garage.garageapi.admin.customer.dto.AdminCustomerPageResponse;
import com.garage.garageapi.admin.customer.dto.AdminCustomerSummaryResponse;
import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.entity.OrderStatus;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.payment.entity.Payment;
import com.garage.garageapi.payment.repository.PaymentRepository;
import com.garage.garageapi.shared.exception.ResourceNotFoundException;
import com.garage.garageapi.user.entity.AuthProvider;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.repository.UserRepository;
import com.garage.garageapi.vehicle.entity.Vehicle;
import com.garage.garageapi.vehicle.repository.VehicleRepository;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminCustomerService {
    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final AddressRepository addressRepository;
    private final VehicleRepository vehicleRepository;

    public AdminCustomerService(UserRepository userRepository, OrderRepository orderRepository,
                                PaymentRepository paymentRepository, AddressRepository addressRepository,
                                VehicleRepository vehicleRepository) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.addressRepository = addressRepository;
        this.vehicleRepository = vehicleRepository;
    }

    @Transactional(readOnly = true)
    public AdminCustomerPageResponse list(String search, Boolean hasOrders, AuthProvider authProvider,
                                          int page, int size) {
        Specification<User> filters = Specification.unrestricted();
        String cleanSearch = clean(search);
        if (cleanSearch != null) {
            String pattern = "%" + cleanSearch.toLowerCase(Locale.ROOT) + "%";
            filters = filters.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("name")), pattern),
                    builder.like(builder.lower(root.get("email")), pattern)));
        }
        if (authProvider != null) {
            filters = filters.and((root, query, builder) ->
                    builder.equal(root.get("authProvider"), authProvider));
        }
        if (hasOrders != null) {
            filters = filters.and((root, query, builder) -> {
                Subquery<Long> orderExists = query.subquery(Long.class);
                var order = orderExists.from(Order.class);
                orderExists.select(builder.literal(1L))
                        .where(builder.equal(order.get("user").get("id"), root.get("id")));
                return hasOrders ? builder.exists(orderExists) : builder.not(builder.exists(orderExists));
            });
        }

        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<User> users = userRepository.findAll(filters, pageable);
        Map<Long, CustomerMetrics> metrics = metrics(
                users.getContent().stream().map(User::getId).toList());
        return AdminCustomerPageResponse.from(users.map(user -> summary(user, metrics.get(user.getId()))));
    }

    @Transactional(readOnly = true)
    public AdminCustomerDetailResponse get(Long userId, int orderPage, int orderSize) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado: " + userId));
        List<Address> addresses = addressRepository.findAllByUserIdOrderByPrimaryDescCreatedAtAsc(userId);
        List<Vehicle> vehicles = vehicleRepository.findAllByUserIdOrderByPrimaryDescCreatedAtAsc(userId);
        CustomerMetrics metric = metrics(List.of(userId)).get(userId);

        Page<Order> orders = orderRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId,
                PageRequest.of(orderPage, orderSize));
        Map<Long, Payment> latestPayments = latestPayments(
                orders.getContent().stream().map(Order::getId).toList());
        List<AdminCustomerDetailResponse.OrderSummary> orderResponses = orders.getContent().stream()
                .map(order -> AdminCustomerDetailResponse.OrderSummary.from(
                        order, latestPayments.get(order.getId()))).toList();

        MetricValues values = values(metric);
        return new AdminCustomerDetailResponse(
                AdminCustomerDetailResponse.Customer.from(user),
                addresses.stream().map(AdminCustomerDetailResponse.AddressSummary::from).toList(),
                vehicles.stream().map(AdminCustomerDetailResponse.VehicleSummary::from).toList(),
                new AdminCustomerDetailResponse.PurchaseSummary(values.totalOrders(),
                        values.confirmedOrders(), values.totalSpent(), values.averageTicket(),
                        values.lastOrderAt()),
                new AdminCustomerDetailResponse.OrderPage(orderResponses, orders.getNumber(), orders.getSize(),
                        orders.getTotalElements(), orders.getTotalPages()));
    }

    private Map<Long, CustomerMetrics> metrics(List<Long> userIds) {
        if (userIds.isEmpty()) return Collections.emptyMap();
        Map<Long, OrderRepository.CustomerConfirmedMetrics> confirmed = orderRepository
                .aggregateCustomerConfirmedMetrics(userIds, OrderStatus.confirmedRevenueStatuses())
                .stream().collect(Collectors.toMap(OrderRepository.CustomerConfirmedMetrics::getUserId,
                        Function.identity()));
        return orderRepository.aggregateCustomerOrderMetrics(userIds).stream().collect(Collectors.toMap(
                OrderRepository.CustomerOrderMetrics::getUserId,
                overall -> new CustomerMetrics(overall, confirmed.get(overall.getUserId()))));
    }

    private Map<Long, Payment> latestPayments(List<Long> orderIds) {
        if (orderIds.isEmpty()) return Collections.emptyMap();
        Map<Long, Payment> result = new LinkedHashMap<>();
        paymentRepository.findAllLatestCandidatesByOrderIds(orderIds)
                .forEach(payment -> result.putIfAbsent(payment.getOrder().getId(), payment));
        return result;
    }

    private AdminCustomerSummaryResponse summary(User user,
                                                  CustomerMetrics metric) {
        MetricValues values = values(metric);
        return new AdminCustomerSummaryResponse(user.getId(), user.getName(), user.getEmail(),
                user.getAuthProvider(), user.isActive(), user.isEmailVerified(), user.getCreatedAt(),
                values.totalOrders(), values.confirmedOrders(), values.totalSpent(),
                values.averageTicket(), values.lastOrderAt());
    }

    private MetricValues values(CustomerMetrics metric) {
        if (metric == null) return new MetricValues(0, 0, ZERO_MONEY, ZERO_MONEY, null);
        long confirmedOrders = metric.confirmed() == null ? 0 : metric.confirmed().getConfirmedOrders();
        BigDecimal totalSpent = money(metric.confirmed() == null ? null : metric.confirmed().getTotalSpent());
        BigDecimal average = confirmedOrders == 0 ? ZERO_MONEY
                : totalSpent.divide(BigDecimal.valueOf(confirmedOrders), 2,
                RoundingMode.HALF_UP);
        return new MetricValues(metric.overall().getTotalOrders(), confirmedOrders, totalSpent,
                average, metric.overall().getLastOrderAt());
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private record MetricValues(long totalOrders, long confirmedOrders, BigDecimal totalSpent,
                                BigDecimal averageTicket, java.time.Instant lastOrderAt) { }

    private record CustomerMetrics(OrderRepository.CustomerOrderMetrics overall,
                                   OrderRepository.CustomerConfirmedMetrics confirmed) { }
}
