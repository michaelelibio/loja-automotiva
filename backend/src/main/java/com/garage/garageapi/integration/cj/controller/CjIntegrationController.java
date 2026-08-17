package com.garage.garageapi.integration.cj.controller;

import com.garage.garageapi.integration.cj.dto.CjProductResponse;
import com.garage.garageapi.integration.cj.dto.CjProductImportResponse;
import com.garage.garageapi.integration.cj.service.CjProductImportService;
import com.garage.garageapi.integration.cj.service.CjProductService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

@Validated
@RestController
@RequestMapping("/api/admin/integrations/cj")
public class CjIntegrationController {
    private final CjProductService productService;
    private final CjProductImportService importService;

    public CjIntegrationController(CjProductService productService, CjProductImportService importService) {
        this.productService = productService;
        this.importService = importService;
    }

    @GetMapping("/products")
    public CjProductResponse products(
            @RequestParam(required = false) @Size(max = 200) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) @Max(1000) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return productService.list(keyword, page, size);
    }

    @GetMapping("/products/{productId}")
    public CjProductResponse.Product product(@PathVariable String productId) {
        return productService.get(productId);
    }

    @PostMapping("/products/{productId}/import")
    @ResponseStatus(HttpStatus.CREATED)
    public CjProductImportResponse importProduct(@PathVariable String productId) {
        return importService.importProduct(productId);
    }
}
