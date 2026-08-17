package com.garage.garageapi.auth.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(name = "app.account.email.mode", havingValue = "development")
public class DevelopmentAccountEmailService implements AccountEmailService {
    private final Map<String, Message> verificationMessages = new ConcurrentHashMap<>();
    private final Map<String, Message> passwordResetMessages = new ConcurrentHashMap<>();

    @Override
    public void sendVerificationEmail(String email, String verificationUrl) {
        verificationMessages.put(email, new Message(email, verificationUrl));
    }

    @Override
    public void sendPasswordResetEmail(String email, String resetUrl) {
        passwordResetMessages.put(email, new Message(email, resetUrl));
    }

    public Message lastVerificationFor(String email) { return verificationMessages.get(email); }
    public Message lastPasswordResetFor(String email) { return passwordResetMessages.get(email); }
    public void clear() {
        verificationMessages.clear();
        passwordResetMessages.clear();
    }

    public record Message(String email, String url) { }
}
