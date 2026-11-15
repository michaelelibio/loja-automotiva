package com.garage.garageapi.order.dto;

import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.payment.entity.PaymentStatus;
import java.math.BigDecimal;

public record OrderFinancialResponse(PaymentStatus status, BigDecimal refundedAmount,
                                     BigDecimal netRevenue, boolean reviewRequired) {
    public static OrderFinancialResponse from(Order order) {
        return new OrderFinancialResponse(order.getPaymentFinancialStatus(), order.getPaymentRefundedAmount(),
                order.getPaymentNetRevenue(), order.isPaymentReviewRequired());
    }
}
