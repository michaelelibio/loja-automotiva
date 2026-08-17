package com.garage.garageapi.order.email;

import com.garage.garageapi.shared.email.ResendEmailClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class OrderEmailModeTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DisabledOrderEmailService.class, DevelopmentOrderEmailService.class,
                    ResendOrderEmailService.class, ResendEmailClient.class)
            .withPropertyValues("app.security.frontend-url=https://garage.example");

    @Test void disabledIsSafeDefault() {
        runner.run(context -> assertThat(context.getBean(OrderEmailService.class))
                .isInstanceOf(DisabledOrderEmailService.class));
    }

    @Test void developmentModeKeepsMessagesInMemory() {
        runner.withPropertyValues("app.account.email.mode=development").run(context ->
                assertThat(context.getBean(OrderEmailService.class)).isInstanceOf(DevelopmentOrderEmailService.class));
    }

    @Test void resendModeUsesSharedConfiguredTransport() {
        runner.withPropertyValues("app.account.email.mode=resend", "app.account.email.resend.api-key=test-key",
                "app.account.email.from=GARAGE <onboarding@resend.dev>").run(context ->
                assertThat(context.getBean(OrderEmailService.class)).isInstanceOf(ResendOrderEmailService.class));
    }
}
