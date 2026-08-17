package com.garage.garageapi.order.email;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class OrderEmailContentTests {
    private final OrderEmailDetails order = new OrderEmailDetails(42L, "Michael", "cliente@example.com",
            new BigDecimal("100.00"), new BigDecimal("18.90"), new BigDecimal("118.90"),
            "Entrega padrão", 8, List.of(new OrderEmailDetails.Item("Pneu", new BigDecimal("50.00"), 2, new BigDecimal("100.00"))));

    @Test void createsHtmlAndPlainTextFromOfficialOrderSnapshot() {
        OrderEmailContent content = OrderEmailContent.create("PAYMENT_APPROVED", order, "https://garage.example/");
        assertThat(content.html()).contains("Pedido #42", "R$", "118,90", "Pneu", "https://garage.example/conta/pedidos/42");
        assertThat(content.text()).contains("Pedido #42", "118,90", "Pneu", "https://garage.example/conta/pedidos/42");
        assertThat(content.html()).isNotBlank();
        assertThat(content.text()).isNotBlank();
    }

    @Test void providesCopyForEverySupportedTransition() {
        assertThat(OrderEmailContent.create("PROCESSING", order, "https://garage.example").subject()).contains("preparação");
        assertThat(OrderEmailContent.create("SHIPPED", order, "https://garage.example").text()).contains("está a caminho");
        assertThat(OrderEmailContent.create("DELIVERED", order, "https://garage.example").subject()).contains("entregue");
    }
}
