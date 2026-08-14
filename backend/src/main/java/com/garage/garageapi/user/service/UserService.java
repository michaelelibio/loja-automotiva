package com.garage.garageapi.user.service;

import com.garage.garageapi.auth.exception.InvalidCredentialsException;
import com.garage.garageapi.auth.exception.UserDisabledException;
import com.garage.garageapi.user.dto.UpdateUserRequest;
import com.garage.garageapi.user.dto.UserResponse;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.repository.UserRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Jwt jwt) {
        return UserResponse.from(findCurrentUser(jwt));
    }

    @Transactional
    public UserResponse updateCurrentUser(Jwt jwt, UpdateUserRequest request) {
        User user = findCurrentUser(jwt);
        user.updateName(normalizeName(request.name()));
        return UserResponse.from(userRepository.saveAndFlush(user));
    }

    @Transactional(readOnly = true)
    public User findCurrentUser(Jwt jwt) {
        Long userId;
        try {
            userId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new InvalidCredentialsException("Token inválido");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("Usuário autenticado não encontrado"));
        if (!user.isActive()) {
            throw new UserDisabledException("Usuário desativado");
        }
        return user;
    }

    private String normalizeName(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }
}
