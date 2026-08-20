package com.garage.garageapi.order.dto;

import com.garage.garageapi.order.entity.OrderItem;

import java.math.BigDecimal;
import com.garage.garageapi.product.entity.FulfillmentType;

public record OrderItemResponse(Long productId, String productName, String productSlug,
                                Long productVariantId, String variantName,
                                FulfillmentType fulfillmentType,
                                BigDecimal unitPrice, Integer quantity, BigDecimal subtotal) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getProductId(), item.getProductName(), item.getProductSlug(),
                item.getProductVariantId(), item.getVariantName(), item.getFulfillmentType(),
                item.getUnitPrice(), item.getQuantity(), item.getSubtotal());
    }
}
