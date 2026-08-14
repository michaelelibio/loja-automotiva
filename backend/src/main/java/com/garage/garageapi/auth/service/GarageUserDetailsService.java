package com.garage.garageapi.auth.service;

import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class GarageUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    public GarageUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas"));
        if (user.getPasswordHash() == null) {
            throw new UsernameNotFoundException("Credenciais inválidas");
        }
        return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .disabled(!user.isActive())
                .authorities("ROLE_" + user.getRole().name())
                .build();
    }
}
