package com.garage.garageapi.favorite.service;

import com.garage.garageapi.auth.exception.InvalidCredentialsException;
import com.garage.garageapi.auth.exception.UserDisabledException;
import com.garage.garageapi.favorite.dto.FavoriteCountResponse;
import com.garage.garageapi.favorite.dto.FavoriteStatusResponse;
import com.garage.garageapi.favorite.entity.Favorite;
import com.garage.garageapi.favorite.exception.InactiveProductException;
import com.garage.garageapi.favorite.repository.FavoriteRepository;
import com.garage.garageapi.product.dto.ProductResponse;
import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.repository.ProductRepository;
import com.garage.garageapi.shared.exception.ResourceNotFoundException;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FavoriteService {
    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public FavoriteService(FavoriteRepository favoriteRepository, UserRepository userRepository,
                           ProductRepository productRepository) {
        this.favoriteRepository = favoriteRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list(Jwt jwt) {
        User user = authenticatedUser(jwt);
        return favoriteRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(Favorite::getProduct)
                .map(ProductResponse::from)
                .toList();
    }

    public ProductResponse add(Jwt jwt, Long productId) {
        User user = authenticatedUser(jwt);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + productId));
        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new InactiveProductException("Produto inativo não pode ser adicionado aos favoritos");
        }
        if (!favoriteRepository.existsByUserIdAndProductId(user.getId(), productId)) {
            try {
                favoriteRepository.saveAndFlush(new Favorite(user, product));
            } catch (DataIntegrityViolationException ignored) {
                // Outra requisição simultânea já criou o mesmo favorito: resultado idempotente.
            }
        }
        return ProductResponse.from(product);
    }

    @Transactional
    public void remove(Jwt jwt, Long productId) {
        User user = authenticatedUser(jwt);
        favoriteRepository.deleteByUserIdAndProductId(user.getId(), productId);
    }

    @Transactional(readOnly = true)
    public FavoriteStatusResponse status(Jwt jwt, Long productId) {
        User user = authenticatedUser(jwt);
        return new FavoriteStatusResponse(
                favoriteRepository.existsByUserIdAndProductId(user.getId(), productId));
    }

    @Transactional(readOnly = true)
    public FavoriteCountResponse count(Jwt jwt) {
        User user = authenticatedUser(jwt);
        return new FavoriteCountResponse(favoriteRepository.countByUserId(user.getId()));
    }

    private User authenticatedUser(Jwt jwt) {
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
}
