package com.garage.garageapi.order.email;

public interface OrderEmailService {
    void sendPaymentApproved(OrderEmailDetails order);
    void sendOrderProcessing(OrderEmailDetails order);
    void sendOrderShipped(OrderEmailDetails order);
    void sendOrderDelivered(OrderEmailDetails order);
}
