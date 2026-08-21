package com.garage.garageapi.integration.cj.service;

import com.garage.garageapi.integration.cj.dto.CjProductVariantSyncResponse;
import com.garage.garageapi.integration.cj.dto.CjProductVariantsResponse;
import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.entity.ProductVariant;
import com.garage.garageapi.product.repository.ProductVariantRepository;
import com.garage.garageapi.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class CjProductVariantSyncService {
    private static final String SUPPLIER = "CJ";
    private static final String CURRENCY_USD = "USD";

    private final CjProductService cjProductService;
    private final ProductVariantRepository variantRepository;

    public CjProductVariantSyncService(CjProductService cjProductService,
                                       ProductVariantRepository variantRepository) {
        this.cjProductService = cjProductService;
        this.variantRepository = variantRepository;
    }

    @Transactional
    public CjProductVariantSyncResponse sync(Product product) {
        validateCjProduct(product);
        String productKeyEn = cjProductService.get(product.getSupplierProductId()).productKeyEn();
        return sync(product, productKeyEn);
    }

    @Transactional
    public CjProductVariantSyncResponse sync(Product product, String productKeyEn) {
        validateCjProduct(product);
        CjProductVariantsResponse response = cjProductService
                .getVariants(product.getSupplierProductId());
        int created = 0;
        int updated = 0;
        int unchanged = 0;

        for (CjProductVariantsResponse.Variant source : response.variants()) {
            Map<String, String> attributes = semanticAttributes(productKeyEn, source.variantKey());
            ProductVariant variant = variantRepository
                    .findBySupplierIgnoreCaseAndSupplierVariantId(SUPPLIER, source.cjVariantId())
                    .orElse(null);
            if (variant == null) {
                variantRepository.save(new ProductVariant(product, SUPPLIER,
                        source.cjVariantId(), source.cjProductId(), source.sku(), source.name(),
                        attributes, source.variantKey(), source.priceUsd(), CURRENCY_USD,
                        source.imageUrl(), source.weightGrams(), decimal(source.lengthMm()),
                        decimal(source.widthMm()), decimal(source.heightMm())));
                created++;
                continue;
            }

            boolean changed = !sameData(variant, source, attributes, product);
            if (changed) {
                variant.updateSupplierData(SUPPLIER, source.cjVariantId(), source.cjProductId(),
                        source.sku(), source.name(), attributes, source.variantKey(),
                        source.priceUsd(), CURRENCY_USD, source.imageUrl(), source.weightGrams(),
                        decimal(source.lengthMm()), decimal(source.widthMm()),
                        decimal(source.heightMm()));
                updated++;
            } else {
                unchanged++;
            }
        }

        return new CjProductVariantSyncResponse(created, updated, unchanged);
    }

    @Transactional
    public CjProductVariantSyncResponse syncProduct(Long productId,
                                                     java.util.function.Function<Long, Product> finder) {
        Product product = finder.apply(productId);
        if (product == null) throw new ResourceNotFoundException("Produto não encontrado: " + productId);
        return sync(product);
    }

    private void validateCjProduct(Product product) {
        if (!SUPPLIER.equalsIgnoreCase(product.getSupplier())
                || !StringUtils.hasText(product.getSupplierProductId())) {
            throw new IllegalArgumentException("Produto não é uma integração CJ válida");
        }
    }

    private boolean sameData(ProductVariant current, CjProductVariantsResponse.Variant source,
                             Map<String, String> attributes, Product product) {
        return Objects.equals(current.getProduct().getId(), product.getId())
                && Objects.equals(current.getSupplierProductId(), source.cjProductId())
                && Objects.equals(current.getSupplierSku(), source.sku())
                && Objects.equals(current.getName(), source.name())
                && Objects.equals(current.getAttributes(), attributes)
                && Objects.equals(current.getRawVariantKey(), source.variantKey())
                && Objects.equals(current.getSupplierCost(), source.priceUsd())
                && Objects.equals(current.getSupplierCostCurrency(), CURRENCY_USD)
                && Objects.equals(current.getImageUrl(), source.imageUrl())
                && Objects.equals(current.getWeightGrams(), source.weightGrams())
                && Objects.equals(current.getLengthMm(), decimal(source.lengthMm()))
                && Objects.equals(current.getWidthMm(), decimal(source.widthMm()))
                && Objects.equals(current.getHeightMm(), decimal(source.heightMm()))
                && Boolean.TRUE.equals(current.getActive());
    }

    static Map<String, String> semanticAttributes(String productKeyEn, String variantKey) {
        String[] values = splitOptions(variantKey);
        if (values.length == 0) return Map.of();
        String[] keys = splitOptions(productKeyEn);
        Map<String, String> attributes = new LinkedHashMap<>();
        if (keys.length == values.length) {
            for (int index = 0; index < keys.length; index++) {
                attributes.put(keys[index], values[index]);
            }
        } else {
            for (int index = 0; index < values.length; index++) {
                attributes.put("option" + (index + 1), values[index]);
            }
        }
        return Map.copyOf(attributes);
    }

    private static String[] splitOptions(String value) {
        if (!StringUtils.hasText(value)) return new String[0];
        String[] options = value.split("-", -1);
        for (int index = 0; index < options.length; index++) {
            options[index] = options[index].trim();
            if (options[index].isEmpty()) return new String[0];
        }
        return options;
    }

    private BigDecimal decimal(Integer value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}
