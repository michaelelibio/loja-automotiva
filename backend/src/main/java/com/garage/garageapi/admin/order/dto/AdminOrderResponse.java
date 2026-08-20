package com.garage.garageapi.admin.order.dto;

import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.entity.OrderItem;
import com.garage.garageapi.order.entity.OrderStatus;
import com.garage.garageapi.payment.entity.Payment;
import com.garage.garageapi.payment.entity.PaymentMethod;
import com.garage.garageapi.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.garage.garageapi.product.entity.FulfillmentType;

public record AdminOrderResponse(
        Long id, OrderStatus status, BigDecimal subtotal, BigDecimal shippingCost, BigDecimal total,
        String shippingCode, String shippingName, Integer shippingEstimatedDays,
        Instant createdAt, Instant expiresAt, Instant updatedAt, Instant processingAt,
        Instant shippedAt, Instant deliveredAt, Customer customer,
        ShippingAddress shippingAddress, List<Item> items, PaymentSummary payment
) {
    public static AdminOrderResponse from(Order order, Payment payment) {
        return new AdminOrderResponse(
                order.getId(), order.getStatus(), order.getSubtotal(), order.getShippingCost(),
                order.getTotal(), order.getShippingCode(), order.getShippingName(),
                order.getShippingEstimatedDays(), order.getCreatedAt(), order.getExpiresAt(), order.getUpdatedAt(),
                order.getProcessingAt(), order.getShippedAt(), order.getDeliveredAt(),
                new Customer(order.getUser().getId(), order.getUser().getName(),
                        order.getUser().getEmail()),
                new ShippingAddress(order.getRecipientName(), order.getZipCode(), order.getStreet(),
                        order.getNumber(), order.getComplement(), order.getNeighborhood(),
                        order.getCity(), order.getState()),
                order.getItems().stream().map(Item::from).toList(),
                payment == null ? null : new PaymentSummary(payment.getMethod(), payment.getStatus(),
                        payment.getPaidAt()));
    }

    public record Customer(Long userId, String name, String email) { }

    public record ShippingAddress(String recipientName, String zipCode, String street, String number,
                                  String complement, String neighborhood, String city, String state) { }

    public record Item(Long productId, String productName, String productSlug,
                       Long productVariantId, String variantName, FulfillmentType fulfillmentType,
                       String supplier, String supplierProductId, String supplierVariantId,
                       String supplierSku, BigDecimal supplierCost, String supplierCostCurrency,
                       BigDecimal weightGrams, BigDecimal lengthMm, BigDecimal widthMm,
                       BigDecimal heightMm, BigDecimal unitPrice, Integer quantity,
                       BigDecimal subtotal) {
        private static Item from(OrderItem item) {
            return new Item(item.getProductId(), item.getProductName(), item.getProductSlug(),
                    item.getProductVariantId(), item.getVariantName(), item.getFulfillmentType(),
                    item.getSupplier(), item.getSupplierProductId(), item.getSupplierVariantId(),
                    item.getSupplierSku(), item.getSupplierCost(), item.getSupplierCostCurrency(),
                    item.getWeightGrams(), item.getLengthMm(), item.getWidthMm(), item.getHeightMm(),
                    item.getUnitPrice(), item.getQuantity(), item.getSubtotal());
        }
    }

    public record PaymentSummary(PaymentMethod method, PaymentStatus status, Instant paidAt) { }
}
