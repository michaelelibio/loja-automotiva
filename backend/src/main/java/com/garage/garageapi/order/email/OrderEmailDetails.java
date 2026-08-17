package com.garage.garageapi.order.email;

import com.garage.garageapi.order.entity.Order;
import java.math.BigDecimal;
import java.util.List;

public record OrderEmailDetails(Long orderId, String customerName, String customerEmail,
                                BigDecimal subtotal, BigDecimal shippingCost, BigDecimal total,
                                String shippingName, Integer shippingEstimatedDays, List<Item> items) {
    public static OrderEmailDetails from(Order order) {
        return new OrderEmailDetails(order.getId(), order.getUser().getName(), order.getUser().getEmail(),
                order.getSubtotal(), order.getShippingCost(), order.getTotal(), order.getShippingName(),
                order.getShippingEstimatedDays(), order.getItems().stream().map(item ->
                        new Item(item.getProductName(), item.getUnitPrice(), item.getQuantity(), item.getSubtotal())).toList());
    }
    public record Item(String name, BigDecimal unitPrice, Integer quantity, BigDecimal subtotal) { }
}
