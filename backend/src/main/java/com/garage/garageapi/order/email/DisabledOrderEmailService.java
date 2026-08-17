package com.garage.garageapi.order.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.account.email.mode", havingValue = "disabled", matchIfMissing = true)
public class DisabledOrderEmailService implements OrderEmailService {
    public void sendPaymentApproved(OrderEmailDetails order) { }
    public void sendOrderProcessing(OrderEmailDetails order) { }
    public void sendOrderShipped(OrderEmailDetails order) { }
    public void sendOrderDelivered(OrderEmailDetails order) { }
}
