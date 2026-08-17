package com.garage.garageapi.integration.cj.service;

import com.garage.garageapi.integration.cj.dto.CjProductImportResponse;
import com.garage.garageapi.integration.cj.dto.CjProductResponse;
import com.garage.garageapi.integration.cj.currency.ExchangeRateService;
import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.entity.FulfillmentType;
import com.garage.garageapi.product.repository.ProductRepository;
import com.garage.garageapi.shared.exception.ResourceConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;

@Service
public class CjProductImportService {
    private static final String SUPPLIER = "CJ";
    private static final String UNCATEGORIZED = "NÃO CATEGORIZADO";

    private final CjProductService cjProductService;
    private final ProductRepository productRepository;
    private final ExchangeRateService exchangeRateService;

    public CjProductImportService(CjProductService cjProductService,
                                  ProductRepository productRepository,
                                  ExchangeRateService exchangeRateService) {
        this.cjProductService = cjProductService;
        this.productRepository = productRepository;
        this.exchangeRateService = exchangeRateService;
    }

    @Transactional
    public CjProductImportResponse importProduct(String requestedProductId) {
        CjProductResponse.Product source = cjProductService.get(requestedProductId);
        String supplierProductId = required(source.cjProductId(), "ID do produto CJ");
        if (productRepository.existsBySupplierIgnoreCaseAndSupplierProductId(SUPPLIER, supplierProductId)) {
            throw new ResourceConflictException("Produto da CJ já importado: " + supplierProductId);
        }

        String name = required(source.name(), "Nome do produto CJ");
        String sku = required(source.sku(), "SKU do produto CJ").toUpperCase(Locale.ROOT);
        if (productRepository.existsBySkuIgnoreCase(sku)) {
            throw new ResourceConflictException("SKU já está em uso");
        }

        BigDecimal supplierCostUsd = source.priceUsd();
        if (supplierCostUsd == null || supplierCostUsd.signum() < 0) {
            throw new IllegalArgumentException("Preço de custo inválido retornado pela CJ");
        }
        BigDecimal exchangeRate = exchangeRateService.usdToBrl();
        if (exchangeRate == null || exchangeRate.signum() <= 0) {
            throw new IllegalArgumentException("Cotação USD/BRL deve ser maior que zero");
        }
        BigDecimal costPrice = supplierCostUsd.multiply(exchangeRate)
                .setScale(2, RoundingMode.HALF_UP);

        String slug = uniqueSlug(name, supplierProductId);
        // Product.price/category are mandatory. These are editable placeholders and the product
        // remains inactive until an administrator defines the commercial catalog data.
        BigDecimal technicalPrice = costPrice.signum() > 0 ? costPrice : new BigDecimal("0.01");
        Product product = new Product(name, slug, null, null, technicalPrice, null,
                UNCATEGORIZED, 0, source.imageUrl(), false);
        product.updateAdmin(name, slug, null, null, technicalPrice, null, costPrice,
                UNCATEGORIZED, source.imageUrl(), false, product.getProductType(), sku);
        product.linkSupplier(SUPPLIER, supplierProductId, supplierCostUsd, exchangeRate,
                Instant.now());
        product.configureFulfillment(FulfillmentType.DROPSHIPPING);

        return CjProductImportResponse.from(productRepository.saveAndFlush(product));
    }

    private String uniqueSlug(String name, String supplierProductId) {
        String base = slugify(name);
        if (!productRepository.existsBySlug(base)) return base;

        String suffix = slugify(supplierProductId);
        int maxBaseLength = 180 - suffix.length() - 1;
        if (maxBaseLength < 1) suffix = suffix.substring(Math.max(0, suffix.length() - 32));
        String shortenedBase = base.substring(0, Math.min(base.length(), 180 - suffix.length() - 1));
        String candidate = shortenedBase + "-" + suffix;
        if (productRepository.existsBySlug(candidate)) {
            throw new ResourceConflictException("Slug do produto da CJ já está em uso");
        }
        return candidate;
    }

    private String slugify(String value) {
        String normalized = Normalizer.normalize(required(value, "Valor para slug"), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (normalized.isBlank()) normalized = "produto";
        return normalized.substring(0, Math.min(normalized.length(), 180));
    }

    private String required(String value, String field) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException(field + " ausente");
        return value.trim();
    }
}
