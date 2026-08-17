package com.garage.garageapi.auth.controller;

import com.garage.garageapi.auth.dto.AuthResponse;
import com.garage.garageapi.auth.dto.GoogleLoginRequest;
import com.garage.garageapi.auth.dto.LoginRequest;
import com.garage.garageapi.auth.dto.RegisterRequest;
import com.garage.garageapi.auth.dto.AccountActionResponse;
import com.garage.garageapi.auth.dto.ForgotPasswordRequest;
import com.garage.garageapi.auth.dto.ResetPasswordRequest;
import com.garage.garageapi.auth.dto.VerifyEmailRequest;
import com.garage.garageapi.auth.service.AccountSecurityService;
import com.garage.garageapi.auth.service.AuthService;
import com.garage.garageapi.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final AccountSecurityService accountSecurityService;

    public AuthController(AuthService authService, AccountSecurityService accountSecurityService) {
        this.authService = authService;
        this.accountSecurityService = accountSecurityService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/google")
    public AuthResponse google(@Valid @RequestBody GoogleLoginRequest request) {
        return authService.googleLogin(request);
    }

    @PostMapping("/verify-email")
    public AccountActionResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        accountSecurityService.verifyEmail(request.token());
        return new AccountActionResponse("E-mail verificado com sucesso.");
    }

    @PostMapping("/forgot-password")
    public AccountActionResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        accountSecurityService.forgotPassword(request.email());
        return new AccountActionResponse(AccountSecurityService.FORGOT_RESPONSE);
    }

    @PostMapping("/reset-password")
    public AccountActionResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        accountSecurityService.resetPassword(request.token(), request.newPassword());
        return new AccountActionResponse("Senha alterada com sucesso.");
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.me(jwt);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }
}
