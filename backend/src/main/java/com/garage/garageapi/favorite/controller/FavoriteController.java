package com.garage.garageapi.favorite.controller;

import com.garage.garageapi.favorite.dto.FavoriteCountResponse;
import com.garage.garageapi.favorite.dto.FavoriteStatusResponse;
import com.garage.garageapi.favorite.service.FavoriteService;
import com.garage.garageapi.product.dto.ProductResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/me/favorites")
public class FavoriteController {
    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping
    public List<ProductResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return favoriteService.list(jwt);
    }

    @PostMapping("/{productId}")
    public ProductResponse add(@AuthenticationPrincipal Jwt jwt, @PathVariable Long productId) {
        return favoriteService.add(jwt, productId);
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable Long productId) {
        favoriteService.remove(jwt, productId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{productId}/status")
    public FavoriteStatusResponse status(@AuthenticationPrincipal Jwt jwt, @PathVariable Long productId) {
        return favoriteService.status(jwt, productId);
    }

    @GetMapping("/count")
    public FavoriteCountResponse count(@AuthenticationPrincipal Jwt jwt) {
        return favoriteService.count(jwt);
    }
}
