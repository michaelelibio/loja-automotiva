package com.garage.garageapi.admin.customer.dto;

import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.entity.OrderStatus;
import com.garage.garageapi.payment.entity.Payment;
import com.garage.garageapi.payment.entity.PaymentMethod;
import com.garage.garageapi.payment.entity.PaymentStatus;
import com.garage.garageapi.user.entity.AuthProvider;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.vehicle.entity.Vehicle;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AdminCustomerDetailResponse(
        Customer customer,
        List<AddressSummary> addresses,
        List<VehicleSummary> vehicles,
        PurchaseSummary purchaseSummary,
        OrderPage orders
) {
    public record Customer(Long id, String name, String email, AuthProvider authProvider,
                           boolean active, boolean emailVerified, Instant createdAt) {
        public static Customer from(User user) {
            return new Customer(user.getId(), user.getName(), user.getEmail(), user.getAuthProvider(),
                    user.isActive(), user.isEmailVerified(), user.getCreatedAt());
        }
    }

    public record AddressSummary(Long id, String label, String recipientName, String zipCode,
                                 String street, String number, String complement, String neighborhood,
                                 String city, String state, boolean primary) {
        public static AddressSummary from(Address address) {
            return new AddressSummary(address.getId(), address.getLabel(), address.getRecipientName(),
                    address.getZipCode(), address.getStreet(), address.getNumber(), address.getComplement(),
                    address.getNeighborhood(), address.getCity(), address.getState(), address.isPrimary());
        }
    }

    public record VehicleSummary(Long id, String brand, String model, Integer year, String version,
                                 String licensePlate, boolean primary, String imageUrl) {
        public static VehicleSummary from(Vehicle vehicle) {
            return new VehicleSummary(vehicle.getId(), vehicle.getBrand(), vehicle.getModel(),
                    vehicle.getYear(), vehicle.getVersion(), vehicle.getLicensePlate(),
                    vehicle.isPrimary(), vehicle.getImageUrl());
        }
    }

    public record PurchaseSummary(long totalOrders, long confirmedOrders, BigDecimal totalSpent,
                                  BigDecimal averageTicket, Instant lastOrderAt) { }

    public record OrderPage(List<OrderSummary> content, int page, int size,
                            long totalElements, int totalPages) { }

    public record OrderSummary(Long id, Instant createdAt, OrderStatus status, BigDecimal total,
                               PaymentSummary payment) {
        public static OrderSummary from(Order order, Payment payment) {
            return new OrderSummary(order.getId(), order.getCreatedAt(), order.getStatus(),
                    order.getTotal(), payment == null ? null : PaymentSummary.from(payment));
        }
    }

    public record PaymentSummary(PaymentMethod method, PaymentStatus status, Instant paidAt) {
        public static PaymentSummary from(Payment payment) {
            return new PaymentSummary(payment.getMethod(), payment.getStatus(), payment.getPaidAt());
        }
    }
}
