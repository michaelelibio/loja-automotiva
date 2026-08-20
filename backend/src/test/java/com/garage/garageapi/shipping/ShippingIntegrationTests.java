package com.garage.garageapi.shipping;

import com.garage.garageapi.auth.service.JwtService;
import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.entity.ProductVariant;
import com.garage.garageapi.product.entity.FulfillmentType;
import com.garage.garageapi.product.repository.ProductRepository;
import com.garage.garageapi.product.repository.ProductVariantRepository;
import com.garage.garageapi.shipping.availability.ProductAvailabilityProvider;
import com.garage.garageapi.integration.cj.service.CjCommerceService;
import com.garage.garageapi.integration.cj.dto.CjFreightResponse;
import com.garage.garageapi.integration.cj.exception.CjIntegrationException;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
@AutoConfigureMockMvc
class ShippingIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @MockitoBean ProductAvailabilityProvider availabilityProvider;
    @MockitoBean CjCommerceService commerceService;

    private User user;

    @BeforeEach
    void setUp() {
        variantRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
        user = userRepository.save(User.local("Cliente", "shipping@example.com",
                passwordEncoder.encode("strongPass123")));
    }

    @AfterEach
    void tearDown() {
        variantRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void validQuoteUsesConfiguredFixedProvider() throws Exception {
        Product product = product("Produto", "produto", "35.90", true, 10);

        quote("89229-030", "[{\"productId\":" + product.getId() + ",\"quantity\":2}]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options.length()").value(1))
                .andExpect(jsonPath("$.options[0].code").value("STANDARD"))
                .andExpect(jsonPath("$.options[0].name").value("Entrega padrão"))
                .andExpect(jsonPath("$.options[0].price").value(18.90))
                .andExpect(jsonPath("$.options[0].estimatedDays").value(8));
        verifyNoInteractions(availabilityProvider, commerceService);
    }

    @Test
    void cjVariantAvailabilityAndFreightUseTrustedVariantAndQuantity() throws Exception {
        Product product = cjProduct("Produto CJ", "produto-cj");
        ProductVariant variant = variant(product, "CJ-VID-1");
        when(availabilityProvider.check("CJ-VID-1", 3)).thenReturn(new ProductAvailabilityProvider.Availability(
                true, List.of(new ProductAvailabilityProvider.Warehouse("1", "CN", 20))));
        when(commerceService.freight(eq("CN"), eq("BR"), eq("89229030"), anyList()))
                .thenReturn(new CjFreightResponse(List.of(new CjFreightResponse.Option(
                        "CJPacket", "7-12", new BigDecimal("5.00"), null, null, null))));

        quote("89229-030", "[{\"productId\":" + product.getId() + ",\"variantId\":"
                + variant.getId() + ",\"quantity\":3}]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options[0].provider").value("CJ"))
                .andExpect(jsonPath("$.options[0].price").value(27.50))
                .andExpect(jsonPath("$.options[0].estimatedDays").value(12))
                .andExpect(jsonPath("$.options[0].legs[0].originCountry").value("CN"));

        verify(commerceService).freight(eq("CN"), eq("BR"), eq("89229030"),
                argThat(products -> products.size() == 1
                        && products.get(0).get("vid").equals("CJ-VID-1")
                        && products.get(0).get("quantity").equals(3)));
    }

    @Test
    void unavailableCjVariantIsConflictAndDoesNotQuoteFreight() throws Exception {
        Product product = cjProduct("Sem estoque", "sem-estoque");
        ProductVariant variant = variant(product, "CJ-VID-OOS");
        when(availabilityProvider.check("CJ-VID-OOS", 1)).thenReturn(
                new ProductAvailabilityProvider.Availability(false,
                        List.of(new ProductAvailabilityProvider.Warehouse("1", "CN", 0))));

        quote("89229030", "[{\"productId\":" + product.getId() + ",\"variantId\":"
                + variant.getId() + ",\"quantity\":1}]")
                .andExpect(status().isConflict());
        verifyNoInteractions(commerceService);
    }

    @Test
    void cjFailureIsBadGatewayInsteadOfOutOfStock() throws Exception {
        Product product = cjProduct("CJ instável", "cj-instavel");
        ProductVariant variant = variant(product, "CJ-VID-FAIL");
        when(availabilityProvider.check(anyString(), anyInt())).thenThrow(new CjIntegrationException(
                "Falha temporária ao consultar disponibilidade da CJ",
                CjIntegrationException.Reason.UPSTREAM));

        quote("89229030", "[{\"productId\":" + product.getId() + ",\"variantId\":"
                + variant.getId() + ",\"quantity\":1}]")
                .andExpect(status().isBadGateway());
    }

    @Test
    void missingCjLogisticsMethodIsRejected() throws Exception {
        Product product = cjProduct("CJ sem frete", "cj-sem-frete");
        ProductVariant variant = variant(product, "CJ-VID-NO-SHIPPING");
        when(availabilityProvider.check(anyString(), anyInt())).thenReturn(
                new ProductAvailabilityProvider.Availability(true,
                        List.of(new ProductAvailabilityProvider.Warehouse("1", "CN", 10))));
        when(commerceService.freight(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(new CjFreightResponse(List.of()));

        quote("89229030", "[{\"productId\":" + product.getId() + ",\"variantId\":"
                + variant.getId() + ",\"quantity\":1}]")
                .andExpect(status().isBadRequest());
    }

    @Test
    void mixedCartKeepsLocalAndCjAsSeparateShippingLegs() throws Exception {
        Product local = product("Local", "local", "20.00", true, 5);
        Product cj = cjProduct("CJ", "cj-misto");
        ProductVariant variant = variant(cj, "CJ-VID-MIX");
        when(availabilityProvider.check("CJ-VID-MIX", 2)).thenReturn(
                new ProductAvailabilityProvider.Availability(true,
                        List.of(new ProductAvailabilityProvider.Warehouse("1", "CN", 10))));
        when(commerceService.freight(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(new CjFreightResponse(List.of(new CjFreightResponse.Option(
                        "CJPacket", "7-12", new BigDecimal("5.00"), null, null, null))));

        quote("89229030", "[{\"productId\":" + local.getId() + ",\"quantity\":1},"
                + "{\"productId\":" + cj.getId() + ",\"variantId\":" + variant.getId()
                + ",\"quantity\":2}]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options[0].provider").value("COMPOSITE"))
                .andExpect(jsonPath("$.options[0].price").value(46.40))
                .andExpect(jsonPath("$.options[0].legs.length()").value(2))
                .andExpect(jsonPath("$.options[0].legs[0].provider").value("LOCAL"))
                .andExpect(jsonPath("$.options[0].legs[1].provider").value("CJ"));
    }

    @Test
    void cjItemsWithoutCommonOriginAreNotNaivelyCombined() throws Exception {
        Product first = cjProduct("CJ China", "cj-china");
        Product second = cjProduct("CJ US", "cj-us");
        ProductVariant firstVariant = variant(first, "CJ-VID-CN");
        ProductVariant secondVariant = variant(second, "CJ-VID-US");
        when(availabilityProvider.check("CJ-VID-CN", 1)).thenReturn(
                new ProductAvailabilityProvider.Availability(true,
                        List.of(new ProductAvailabilityProvider.Warehouse("1", "CN", 10))));
        when(availabilityProvider.check("CJ-VID-US", 1)).thenReturn(
                new ProductAvailabilityProvider.Availability(true,
                        List.of(new ProductAvailabilityProvider.Warehouse("2", "US", 10))));

        quote("89229030", "[{\"productId\":" + first.getId() + ",\"variantId\":"
                + firstVariant.getId() + ",\"quantity\":1},{\"productId\":" + second.getId()
                + ",\"variantId\":" + secondVariant.getId() + ",\"quantity\":1}]")
                .andExpect(status().isConflict());
        verifyNoInteractions(commerceService);
    }

    @Test
    void invalidZipCodeIsRejected() throws Exception {
        Product product = product("Produto", "produto", "10.00", true, 10);
        quote("123", "[{\"productId\":" + product.getId() + ",\"quantity\":1}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.zipCode").exists());
    }

    @Test
    void missingProductIsRejected() throws Exception {
        quote("89229030", "[{\"productId\":999999,\"quantity\":1}]")
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidQuantityIsRejected() throws Exception {
        Product product = product("Produto", "produto", "10.00", true, 10);
        quote("89229030", "[{\"productId\":" + product.getId() + ",\"quantity\":0}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields['items[0].quantity']").exists());
    }

    @Test
    void multipleItemsAreValidatedAndClientCommercialValuesAreIgnored() throws Exception {
        Product first = product("Produto A", "produto-a", "999.90", true, 10);
        Product second = product("Produto B", "produto-b", "0.01", true, 10);
        String items = "[{\"productId\":" + first.getId()
                + ",\"quantity\":2,\"price\":0,\"subtotal\":0},{\"productId\":"
                + second.getId() + ",\"quantity\":3,\"price\":100000}]";

        quote("89229-030", items)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options[0].price").value(18.90));
    }

    @Test
    void inactiveProductFollowsCurrentCatalogRule() throws Exception {
        Product product = product("Inativo", "inativo", "10.00", false, 10);
        quote("89229030", "[{\"productId\":" + product.getId() + ",\"quantity\":1}]")
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions quote(String zipCode, String items)
            throws Exception {
        return mockMvc.perform(post("/api/shipping/quote")
                .header("Authorization", "Bearer " + jwtService.issue(user).value())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"zipCode\":\"" + zipCode + "\",\"items\":" + items + "}"));
    }

    private Product product(String name, String slug, String price, boolean active, int stock) {
        return productRepository.save(new Product(name, slug, null, null, new BigDecimal(price),
                null, "Categoria", stock, null, active));
    }

    private Product cjProduct(String name, String slug) {
        Product product = new Product(name, slug, null, null, new BigDecimal("100.00"),
                null, "Categoria", 0, null, true);
        product.updateAdmin(name, slug, null, null, new BigDecimal("100.00"), null,
                new BigDecimal("50.00"), "Categoria", null, true, product.getProductType(),
                "SKU-" + slug);
        product.linkSupplier("CJ", "CJ-PID-" + slug, new BigDecimal("10.00"),
                new BigDecimal("5.50"), Instant.now());
        product.configureFulfillment(FulfillmentType.DROPSHIPPING);
        return productRepository.save(product);
    }

    private ProductVariant variant(Product product, String supplierVariantId) {
        return variantRepository.save(new ProductVariant(product, "CJ", supplierVariantId,
                product.getSupplierProductId(), "SKU-" + supplierVariantId, "Opção",
                Map.of("option1", "Opção"), new BigDecimal("10.00"), "USD", null,
                new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("10"),
                new BigDecimal("10")));
    }
}
