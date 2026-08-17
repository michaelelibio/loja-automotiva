package com.garage.garageapi.auth.controller;

import com.garage.garageapi.auth.dto.AccountActionResponse;
import com.garage.garageapi.auth.dto.ChangePasswordRequest;
import com.garage.garageapi.auth.service.AccountSecurityService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final AccountSecurityService accountSecurityService;

    public AccountController(AccountSecurityService accountSecurityService) {
        this.accountSecurityService = accountSecurityService;
    }

    @PutMapping("/password")
    public AccountActionResponse changePassword(@AuthenticationPrincipal Jwt jwt,
                                                @Valid @RequestBody ChangePasswordRequest request) {
        accountSecurityService.changePassword(jwt, request.currentPassword(), request.newPassword());
        return new AccountActionResponse("Senha alterada com sucesso.");
    }
}
