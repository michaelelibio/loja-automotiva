package com.garage.garageapi.auth.email;

public interface AccountEmailService {
    void sendVerificationEmail(String email, String verificationUrl);
    void sendPasswordResetEmail(String email, String resetUrl);
}
