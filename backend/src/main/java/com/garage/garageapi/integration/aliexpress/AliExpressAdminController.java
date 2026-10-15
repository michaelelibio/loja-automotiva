package com.garage.garageapi.integration.aliexpress;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/integrations/aliexpress")
@PreAuthorize("hasRole('ADMIN')")
public class AliExpressAdminController {
    private final AliExpressProductService products;
    private final AliExpressOAuthStateService states;
    public AliExpressAdminController(AliExpressProductService products, AliExpressOAuthStateService states) {
        this.products = products; this.states = states;
    }
    public record PreviewRequest(@NotBlank @Size(max=2048) String input,
            @NotBlank @Pattern(regexp="[A-Z]{2}") String shipToCountry,
            @NotBlank @Pattern(regexp="[A-Z]{3}") String targetCurrency,
            @NotBlank @Pattern(regexp="[a-z]{2}(?:_[A-Z]{2})?") String targetLanguage) {}
    public record ImportRequest(@NotBlank @Size(max=2048) String input,
            @NotBlank @Pattern(regexp="[A-Z]{2}") String shipToCountry,
            @NotBlank @Pattern(regexp="[A-Z]{3}") String targetCurrency,
            @NotBlank @Pattern(regexp="[a-z]{2}(?:_[A-Z]{2})?") String targetLanguage,
            @NotBlank @Pattern(regexp="[a-f0-9]{64}") String fingerprint,
            @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal salePriceBrl,
            @Size(max=1000) Map<@NotBlank @Size(max=150) String,@NotNull @Min(0) Integer> variantStocks) {}
    public record StockRequest(@NotNull @Min(0) Integer quantity) {}
    @PostMapping("/authorize")
    public AliExpressOAuthStateService.AuthorizationUrl authorize() { return states.start(); }
    @PostMapping("/preview")
    public AliExpressProductPreview preview(@Valid @RequestBody PreviewRequest request) {
        return products.preview(request.input(),request.shipToCountry(),request.targetCurrency(),request.targetLanguage());
    }
    @PostMapping("/import") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> importProduct(@Valid @RequestBody ImportRequest request) { return products.importProduct(request); }
    @PatchMapping("/products/{productId}/variants/{variantId}/stock")
    public Map<String,Object> reviewStock(@PathVariable Long productId,@PathVariable Long variantId,@Valid @RequestBody StockRequest request) {
        return products.reviewStock(productId,variantId,request.quantity());
    }
    @GetMapping("/products/{productId}/stock")
    public java.util.List<Map<String,Object>> stock(@PathVariable Long productId) { return products.stock(productId); }
}
