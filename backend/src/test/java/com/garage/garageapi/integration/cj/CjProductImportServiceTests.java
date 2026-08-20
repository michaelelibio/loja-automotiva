package com.garage.garageapi.integration.cj;

import com.garage.garageapi.integration.cj.dto.CjProductImportResponse;
import com.garage.garageapi.integration.cj.dto.CjProductResponse;
import com.garage.garageapi.integration.cj.dto.CjProductVariantsResponse;
import com.garage.garageapi.integration.cj.exception.CjIntegrationException;
import com.garage.garageapi.integration.cj.currency.ExchangeRateService;
import com.garage.garageapi.integration.cj.service.CjProductImportService;
import com.garage.garageapi.integration.cj.service.CjProductService;
import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.entity.FulfillmentType;
import com.garage.garageapi.product.repository.ProductRepository;
import com.garage.garageapi.shared.exception.ResourceConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class CjProductImportServiceTests {
    @Autowired CjProductImportService importService;
    @Autowired ProductRepository productRepository;
    @MockitoBean CjProductService cjProductService;
    @MockitoBean ExchangeRateService exchangeRateService;

    @BeforeEach
    void exchangeRate() {
        when(exchangeRateService.usdToBrl()).thenReturn(new BigDecimal("5.50"));
        when(cjProductService.getVariants(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> new CjProductVariantsResponse(
                        invocation.getArgument(0), List.of()));
    }

    @Test
    void importsInactiveCjProductWithCostAndNoFictitiousStock() {
        when(cjProductService.get("cj-100")).thenReturn(source("cj-100", "Car Cleaner", "CJ-CLEAN-1"));

        CjProductImportResponse result = importService.importProduct("cj-100");
        Product saved = productRepository.findById(result.id()).orElseThrow();

        assertThat(saved.getSupplier()).isEqualTo("CJ");
        assertThat(saved.getSupplierProductId()).isEqualTo("cj-100");
        assertThat(saved.getSupplierCostUsd()).isEqualByComparingTo("1.90");
        assertThat(saved.getSupplierExchangeRate()).isEqualByComparingTo("5.50");
        assertThat(saved.getSupplierCostUpdatedAt()).isNotNull();
        assertThat(saved.getCostPrice()).isEqualByComparingTo("10.45");
        assertThat(saved.getPrice()).isEqualByComparingTo("10.45");
        assertThat(saved.getActive()).isFalse();
        assertThat(saved.getStockQuantity()).isZero();
        assertThat(saved.getFulfillmentType()).isEqualTo(FulfillmentType.DROPSHIPPING);
        assertThat(saved.getCategory()).isEqualTo("NÃO CATEGORIZADO");
        assertThat(saved.getSku()).isEqualTo("CJ-CLEAN-1");
    }

    @Test
    void repeatedSupplierProductIsRejected() {
        when(cjProductService.get("cj-101")).thenReturn(source("cj-101", "Car Wax", "CJ-WAX-1"));
        importService.importProduct("cj-101");

        assertThatThrownBy(() -> importService.importProduct("cj-101"))
                .isInstanceOf(ResourceConflictException.class);
    }

    @Test
    void duplicateSlugReceivesDeterministicSupplierProductSuffix() {
        productRepository.saveAndFlush(localProduct("Car Sticker", "car-sticker", "LOCAL-1"));
        when(cjProductService.get("1410451269214146560"))
                .thenReturn(source("1410451269214146560", "Car Sticker", "CJ-STICKER-1"));

        CjProductImportResponse result = importService.importProduct("1410451269214146560");

        assertThat(result.slug()).isEqualTo("car-sticker-1410451269214146560");
    }

    @Test
    void duplicateSkuIsRejectedWithoutCreatingImportedProduct() {
        productRepository.saveAndFlush(localProduct("Local", "local", "SAME-SKU"));
        long before = productRepository.count();
        when(cjProductService.get("cj-102")).thenReturn(source("cj-102", "Remote", "same-sku"));

        assertThatThrownBy(() -> importService.importProduct("cj-102"))
                .isInstanceOf(ResourceConflictException.class);
        assertThat(productRepository.count()).isEqualTo(before);
    }

    @Test
    void cjFailureDoesNotCreatePartialProduct() {
        long before = productRepository.count();
        when(cjProductService.get("cj-fail")).thenThrow(new CjIntegrationException(
                "CJ indisponível", CjIntegrationException.Reason.UPSTREAM));

        assertThatThrownBy(() -> importService.importProduct("cj-fail"))
                .isInstanceOf(CjIntegrationException.class);
        assertThat(productRepository.count()).isEqualTo(before);
    }

    @Test
    void conversionUsesExplicitHalfUpRounding() {
        when(cjProductService.get("cj-round")).thenReturn(new CjProductResponse.Product(
                "cj-round", "Rounded", null, new BigDecimal("1.999"), null, null,
                "CJ-ROUND"));
        when(exchangeRateService.usdToBrl()).thenReturn(new BigDecimal("5.555"));

        Product saved = productRepository.findById(importService.importProduct("cj-round").id())
                .orElseThrow();

        assertThat(saved.getCostPrice()).isEqualByComparingTo("11.10");
        assertThat(saved.getPrice()).isEqualByComparingTo("11.10");
    }

    @Test
    void invalidExchangeRateDoesNotCreatePartialProduct() {
        when(cjProductService.get("cj-rate-fail"))
                .thenReturn(source("cj-rate-fail", "Rate fail", "CJ-RATE-FAIL"));
        when(exchangeRateService.usdToBrl()).thenReturn(BigDecimal.ZERO);
        long before = productRepository.count();

        assertThatThrownBy(() -> importService.importProduct("cj-rate-fail"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(productRepository.count()).isEqualTo(before);
    }

    @Test
    void manualProductKeepsSupplierMetadataNull() {
        Product manual = productRepository.saveAndFlush(localProduct("Manual", "manual", "MANUAL-1"));

        assertThat(manual.getSupplier()).isNull();
        assertThat(manual.getSupplierProductId()).isNull();
        assertThat(manual.getSupplierCostUsd()).isNull();
        assertThat(manual.getSupplierExchangeRate()).isNull();
        assertThat(manual.getSupplierCostUpdatedAt()).isNull();
    }

    private CjProductResponse.Product source(String id, String name, String sku) {
        return new CjProductResponse.Product(id, name, "https://example.test/image.jpg",
                new BigDecimal("1.90"), "cj-category", "CJ Category", sku);
    }

    private Product localProduct(String name, String slug, String sku) {
        Product product = new Product(name, slug, null, null, new BigDecimal("10.00"), null,
                "LOCAL", 0, null, true);
        product.updateAdmin(name, slug, null, null, product.getPrice(), null,
                new BigDecimal("5.00"), "LOCAL", null, true, product.getProductType(), sku);
        return product;
    }
}
