package com.garage.garageapi.auth.service;

import com.garage.garageapi.auth.dto.AuthResponse;
import com.garage.garageapi.auth.dto.GoogleLoginRequest;
import com.garage.garageapi.auth.dto.LoginRequest;
import com.garage.garageapi.auth.dto.RegisterRequest;
import com.garage.garageapi.auth.exception.InvalidCredentialsException;
import com.garage.garageapi.auth.exception.UserDisabledException;
import com.garage.garageapi.shared.exception.ResourceConflictException;
import com.garage.garageapi.user.dto.UserResponse;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.repository.UserRepository;
import com.garage.garageapi.user.service.UserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final GoogleTokenValidator googleTokenValidator;
    private final UserService userService;
    private final AccountSecurityService accountSecurityService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, JwtService jwtService,
                       GoogleTokenValidator googleTokenValidator, UserService userService,
                       AccountSecurityService accountSecurityService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.googleTokenValidator = googleTokenValidator;
        this.userService = userService;
        this.accountSecurityService = accountSecurityService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResourceConflictException("E-mail já cadastrado");
        }
        User user = User.local(request.name().trim(), email, passwordEncoder.encode(request.password()));
        try {
            User saved = userRepository.saveAndFlush(user);
            accountSecurityService.sendVerification(saved);
            return response(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new ResourceConflictException("E-mail já cadastrado");
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (DisabledException exception) {
            throw new UserDisabledException("Usuário desativado");
        } catch (BadCredentialsException exception) {
            throw new InvalidCredentialsException("Credenciais inválidas");
        } catch (AuthenticationException exception) {
            throw new InvalidCredentialsException("Credenciais inválidas");
        }
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new InvalidCredentialsException("Credenciais inválidas"));
        return response(user);
    }

    @Transactional
    public AuthResponse googleLogin(GoogleLoginRequest request) {
        GoogleTokenValidator.GoogleIdentity identity = googleTokenValidator.validate(request.credential());
        User user = userRepository.findByGoogleSubject(identity.subject()).orElse(null);
        if (user == null) {
            String email = normalizeEmail(identity.email());
            if (userRepository.existsByEmailIgnoreCase(email)) {
                throw new ResourceConflictException(
                        "Já existe uma conta com este e-mail. Entre com e-mail e senha antes de vincular o Google.");
            }
            String name = identity.name() == null || identity.name().isBlank()
                    ? email.substring(0, Math.min(email.length(), 150))
                    : identity.name().trim().substring(0, Math.min(identity.name().trim().length(), 150));
            String pictureUrl = identity.pictureUrl() == null ? null
                    : identity.pictureUrl().substring(0, Math.min(identity.pictureUrl().length(), 1000));
            user = User.google(name, email, identity.subject(), pictureUrl);
            try {
                user = userRepository.saveAndFlush(user);
            } catch (DataIntegrityViolationException exception) {
                throw new ResourceConflictException("Conta Google já cadastrada");
            }
        }
        user.verifyEmail();
        ensureActive(user);
        return response(user);
    }

    @Transactional(readOnly = true)
    public UserResponse me(Jwt jwt) {
        return userService.getCurrentUser(jwt);
    }

    private AuthResponse response(User user) {
        ensureActive(user);
        JwtService.Token token = jwtService.issue(user);
        return new AuthResponse(token.value(), "Bearer", token.expiresIn(), UserResponse.from(user));
    }

    private void ensureActive(User user) {
        if (!user.isActive()) throw new UserDisabledException("Usuário desativado");
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
