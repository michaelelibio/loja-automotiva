package com.garage.garageapi.order.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@ConditionalOnProperty(name = "app.account.email.mode", havingValue = "development")
public class DevelopmentOrderEmailService implements OrderEmailService {
    private final List<Message> messages = new CopyOnWriteArrayList<>();
    private final String frontendBaseUrl;
    public DevelopmentOrderEmailService(@Value("${app.security.frontend-url}") String frontendBaseUrl) { this.frontendBaseUrl = frontendBaseUrl; }
    public void sendPaymentApproved(OrderEmailDetails order) { add("PAYMENT_APPROVED", order); }
    public void sendOrderProcessing(OrderEmailDetails order) { add("PROCESSING", order); }
    public void sendOrderShipped(OrderEmailDetails order) { add("SHIPPED", order); }
    public void sendOrderDelivered(OrderEmailDetails order) { add("DELIVERED", order); }
    private void add(String type, OrderEmailDetails order) {
        OrderEmailContent content = OrderEmailContent.create(type, order, frontendBaseUrl);
        messages.add(new Message(type, order, content.subject(), content.html(), content.text()));
    }
    public List<Message> messages() { return List.copyOf(messages); }
    public void clear() { messages.clear(); }
    public record Message(String type, OrderEmailDetails order, String subject, String html, String text) { }
}
