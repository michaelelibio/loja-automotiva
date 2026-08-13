package com.garage.garageapi.auth.service;

import com.garage.garageapi.auth.exception.InvalidGoogleTokenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

@Component
public class GoogleTokenValidator {
    private static final String GOOGLE_JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private final NimbusJwtDecoder decoder;

    public GoogleTokenValidator(@Value("${app.security.google.client-id}") String clientId) {
        decoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWK_SET_URI).build();
        OAuth2TokenValidator<Jwt> defaults = JwtValidators.createDefault();
        OAuth2TokenValidator<Jwt> issuer = jwt -> {
            String value = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
            return ("https://accounts.google.com".equals(value) || "accounts.google.com".equals(value))
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Issuer Google inválido", null));
        };
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience().contains(clientId)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Audience Google inválida", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(defaults, issuer, audience));
    }

    public GoogleIdentity validate(String credential) {
        try {
            Jwt jwt = decoder.decode(credential);
            String subject = jwt.getSubject();
            String email = jwt.getClaimAsString("email");
            Boolean emailVerified = jwt.getClaim("email_verified");
            if (subject == null || subject.length() > 255 || email == null || email.length() > 320
                    || !Boolean.TRUE.equals(emailVerified)) {
                throw new InvalidGoogleTokenException("Token Google sem identidade ou e-mail verificado");
            }
            String name = jwt.getClaimAsString("name");
            String picture = jwt.getClaimAsString("picture");
            return new GoogleIdentity(subject, email, name, picture);
        } catch (JwtException exception) {
            throw new InvalidGoogleTokenException("Token Google inválido ou expirado");
        }
    }

    public record GoogleIdentity(String subject, String email, String name, String pictureUrl) { }
}
