package com.garage.garageapi.admin.product;

import com.garage.garageapi.admin.product.dto.AdminProductActiveRequest;
import com.garage.garageapi.admin.product.dto.AdminProductPageResponse;
import com.garage.garageapi.admin.product.dto.AdminProductRequest;
import com.garage.garageapi.admin.product.dto.AdminProductResponse;
import com.garage.garageapi.admin.product.dto.AdminProductUpdateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {
    private final AdminProductService productService;

    public AdminProductController(AdminProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public AdminProductPageResponse list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return productService.list(search, active, category, page, size);
    }

    @GetMapping("/{id}")
    public AdminProductResponse get(@PathVariable Long id) { return productService.get(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminProductResponse create(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody AdminProductRequest request) {
        return productService.create(jwt, request);
    }

    @PutMapping("/{id}")
    public AdminProductResponse update(@PathVariable Long id,
                                       @Valid @RequestBody AdminProductUpdateRequest request) {
        return productService.update(id, request);
    }

    @PatchMapping("/{id}/active")
    public AdminProductResponse setActive(@PathVariable Long id,
                                          @Valid @RequestBody AdminProductActiveRequest request) {
        return productService.setActive(id, request.active());
    }
}
