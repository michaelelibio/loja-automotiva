package com.garage.garageapi.auth.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.account.email.mode", havingValue = "disabled", matchIfMissing = true)
public class DisabledAccountEmailService implements AccountEmailService {
    @Override
    public void sendVerificationEmail(String email, String verificationUrl) { }

    @Override
    public void sendPasswordResetEmail(String email, String resetUrl) { }
}
