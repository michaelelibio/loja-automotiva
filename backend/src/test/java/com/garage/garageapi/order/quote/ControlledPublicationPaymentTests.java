package com.garage.garageapi.order.quote;

import com.garage.garageapi.payment.gateway.CheckoutProGateway;
import com.garage.garageapi.payment.repository.PaymentRepository;
import com.garage.garageapi.payment.service.PaymentAttemptService;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.shared.exception.ResourceConflictException;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="app.payment.creation-enabled=false")
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@Transactional
class ControlledPublicationPaymentTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @MockitoBean CheckoutProGateway gateway;

    @Test void disabledPaymentEndpointCannotCreateOrReturnCheckout() throws Exception {
        var user = users.saveAndFlush(User.local("Cliente Homologacao", "controlled@example.test", "hash"));
        mvc.perform(post("/api/orders/987654/payments")
                .with(jwt().jwt(j -> j.subject(user.getId().toString())))
                .contentType("application/json").content("{\"method\":\"MERCADO_PAGO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Pagamentos temporariamente indisponíveis durante a validação da loja."))
                .andExpect(jsonPath("$.checkoutUrl").doesNotExist());
        verifyNoInteractions(gateway);
    }

    @Test void disabledFlagBlocksBothAttemptEntryPointsBeforeDatabaseOrProviderAccess() {
        var payments = mock(PaymentRepository.class);
        var orders = mock(OrderRepository.class);
        var service = new PaymentAttemptService(payments, orders, gateway, false);
        assertThatThrownBy(() -> service.prepare(1L, 2L)).isInstanceOf(ResourceConflictException.class);
        assertThatThrownBy(() -> service.createCheckoutPreference(3L, null)).isInstanceOf(ResourceConflictException.class);
        verifyNoInteractions(payments, orders, gateway);
    }
}
