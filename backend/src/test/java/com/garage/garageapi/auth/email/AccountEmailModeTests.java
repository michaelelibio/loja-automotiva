package com.garage.garageapi.auth.email;

import com.garage.garageapi.shared.email.ResendEmailClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AccountEmailModeTests {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DevelopmentAccountEmailService.class,
                    DisabledAccountEmailService.class, ResendAccountEmailService.class,
                    ResendEmailClient.class);

    @Test
    void developmentModeSelectsDevelopmentImplementation() {
        contextRunner.withPropertyValues("app.account.email.mode=development")
                .run(context -> {
                    assertThat(context).hasSingleBean(AccountEmailService.class);
                    assertThat(context.getBean(AccountEmailService.class))
                            .isInstanceOf(DevelopmentAccountEmailService.class);
                });
    }

    @Test
    void disabledModeSelectsSafeNoOpImplementation() {
        contextRunner.withPropertyValues("app.account.email.mode=disabled")
                .run(context -> {
                    assertThat(context).hasSingleBean(AccountEmailService.class);
                    AccountEmailService service = context.getBean(AccountEmailService.class);
                    assertThat(service).isInstanceOf(DisabledAccountEmailService.class);
                    service.sendVerificationEmail("nobody@example.com", "https://example.com/token");
                    service.sendPasswordResetEmail("nobody@example.com", "https://example.com/reset");
                });
    }

    @Test
    void resendModeSelectsResendImplementation() {
        contextRunner.withPropertyValues(
                        "app.account.email.mode=resend",
                        "app.account.email.resend.api-key=test-only-resend-key",
                        "app.account.email.from=GARAGE <onboarding@resend.dev>")
                .run(context -> {
                    assertThat(context).hasSingleBean(AccountEmailService.class);
                    assertThat(context.getBean(AccountEmailService.class))
                            .isInstanceOf(ResendAccountEmailService.class);
                });
    }

    @Test
    void resendModeRefusesToStartWithoutApiKey() {
        contextRunner.withPropertyValues("app.account.email.mode=resend")
                .run(context -> assertThat(context).hasFailed());
    }
}
