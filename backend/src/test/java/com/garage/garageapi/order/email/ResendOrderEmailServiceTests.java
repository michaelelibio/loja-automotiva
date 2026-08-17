package com.garage.garageapi.order.email;

import com.garage.garageapi.shared.email.ResendEmailClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.util.List;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ResendOrderEmailServiceTests {
    @Test void assemblesResendRequestWithoutExternalCall() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.resend.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ResendOrderEmailService service = new ResendOrderEmailService(
                new ResendEmailClient(builder.build(), "test-key", "GARAGE <onboarding@resend.dev>"),
                "https://garage.example");
        server.expect(once(), requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("cliente@example.com")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Pedido #42")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("https://garage.example/conta/pedidos/42")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"text\"")))
                .andRespond(withSuccess("{\"id\":\"email-1\"}", MediaType.APPLICATION_JSON));
        service.sendOrderShipped(details());
        server.verify();
    }

    private OrderEmailDetails details() {
        return new OrderEmailDetails(42L, "Cliente", "cliente@example.com", new BigDecimal("100.00"),
                new BigDecimal("18.90"), new BigDecimal("118.90"), "Entrega padrão", 8,
                List.of(new OrderEmailDetails.Item("Pneu", new BigDecimal("50.00"), 2, new BigDecimal("100.00"))));
    }
}
