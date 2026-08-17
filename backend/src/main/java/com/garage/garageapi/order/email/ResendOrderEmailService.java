package com.garage.garageapi.order.email;

import com.garage.garageapi.shared.email.ResendEmailClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.account.email.mode", havingValue = "resend")
public class ResendOrderEmailService implements OrderEmailService {
    private final ResendEmailClient client;
    private final String frontendBaseUrl;
    public ResendOrderEmailService(ResendEmailClient client, @Value("${app.security.frontend-url}") String frontendBaseUrl) {
        this.client = client; this.frontendBaseUrl = frontendBaseUrl;
    }
    public void sendPaymentApproved(OrderEmailDetails order) { send("PAYMENT_APPROVED", order); }
    public void sendOrderProcessing(OrderEmailDetails order) { send("PROCESSING", order); }
    public void sendOrderShipped(OrderEmailDetails order) { send("SHIPPED", order); }
    public void sendOrderDelivered(OrderEmailDetails order) { send("DELIVERED", order); }
    private void send(String type, OrderEmailDetails order) {
        OrderEmailContent content = OrderEmailContent.create(type, order, frontendBaseUrl);
        client.send(order.customerEmail(), content.subject(), content.html(), content.text());
    }
}
