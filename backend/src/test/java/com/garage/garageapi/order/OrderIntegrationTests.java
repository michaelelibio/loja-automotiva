package com.garage.garageapi.order;

import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.address.repository.AddressRepository;
import com.garage.garageapi.auth.service.JwtService;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.order.repository.OrderItemRepository;
import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.entity.ProductVariant;
import com.garage.garageapi.product.entity.FulfillmentType;
import com.garage.garageapi.product.repository.ProductRepository;
import com.garage.garageapi.product.repository.ProductVariantRepository;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.repository.UserRepository;
import com.garage.garageapi.shipping.provider.FixedShippingProvider;
import com.garage.garageapi.shipping.provider.ShippingProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.garage.garageapi.shipping.availability.ProductAvailabilityProvider;
import com.garage.garageapi.integration.cj.service.CjCommerceService;
import com.garage.garageapi.integration.cj.dto.CjFreightResponse;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.reset;

@SpringBootTest
@AutoConfigureMockMvc
class OrderIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderItemRepository orderItemRepository;
    @Autowired AddressRepository addressRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository productVariantRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @MockitoBean ProductAvailabilityProvider availabilityProvider;
    @MockitoBean CjCommerceService commerceService;

    @BeforeEach
    void cleanBefore() {
        cleanDatabase();
        when(availabilityProvider.check(anyString(), anyInt())).thenReturn(
                new ProductAvailabilityProvider.Availability(true,
                        java.util.List.of(new ProductAvailabilityProvider.Warehouse("1", "CN", 1000))));
        when(commerceService.freight(anyString(), eq("BR"), anyString(), anyList()))
                .thenReturn(new CjFreightResponse(java.util.List.of(new CjFreightResponse.Option(
                        "CJPacket", "7-12", new BigDecimal("5.00"), null, null, null))));
    }

    @AfterEach
    void cleanAfter() { cleanDatabase(); }

    @Test
    void endpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/orders/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":1,\"items\":[{\"productId\":1,\"quantity\":1}]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validOrderUsesDatabasePriceAndCalculatesTotals() throws Exception {
        User user = user("user@example.com");
        Address address = address(user, "Rua Original", "100");
        Product product = product("Cera Premium", "cera-premium", "89.90", true);

        mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(request(address.getId(),
                                item(product.getId(), 2))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.subtotal").value(179.80))
                .andExpect(jsonPath("$.shippingCost").value(18.90))
                .andExpect(jsonPath("$.total").value(198.70))
                .andExpect(jsonPath("$.shipping.code").value("STANDARD"))
                .andExpect(jsonPath("$.shipping.name").value("Entrega padrão"))
                .andExpect(jsonPath("$.shipping.price").value(18.90))
                .andExpect(jsonPath("$.shipping.estimatedDays").value(8))
                .andExpect(jsonPath("$.shippingAddress.recipientName").value("Michael"))
                .andExpect(jsonPath("$.shippingAddress.street").value("Rua Original"))
                .andExpect(jsonPath("$.items[0].productId").value(product.getId()))
                .andExpect(jsonPath("$.items[0].productName").value("Cera Premium"))
                .andExpect(jsonPath("$.items[0].productSlug").value("cera-premium"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(89.90))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].subtotal").value(179.80));

        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(98);
        var savedOrder = orderRepository.findAll().get(0);
        assertThat(Duration.between(savedOrder.getCreatedAt(), savedOrder.getExpiresAt()))
                .isEqualTo(Duration.ofHours(24));
    }

    @Test
    void multipleItemsWorkAndClientCannotSupplyCommercialValues() throws Exception {
        User user = user("user@example.com");
        Address address = address(user, "Rua", "10");
        Product first = product("Produto A", "produto-a", "10.25", true);
        Product second = product("Kit B", "kit-b", "20.50", true);

        String body = "{\"addressId\":" + address.getId() + ",\"status\":\"PAID\",\"total\":0,"
                + "\"items\":[{\"productId\":" + first.getId() + ",\"quantity\":2,\"unitPrice\":0},"
                + "{\"productId\":" + second.getId() + ",\"quantity\":3}]}";
        mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.subtotal").value(82.00))
                .andExpect(jsonPath("$.total").value(100.90));

        assertThat(productRepository.findById(first.getId()).orElseThrow().getStockQuantity()).isEqualTo(98);
        assertThat(productRepository.findById(second.getId()).orElseThrow().getStockQuantity()).isEqualTo(97);
    }

    @Test
    void orderRequotesSelectedShippingAndKeepsHistoricalSnapshot() throws Exception {
        User user = user("shipping-order@example.com");
        Address address = address(user, "Rua", "10");
        Product product = product("Produto Frete", "produto-frete", "40.00", true);
        String body = "{\"addressId\":" + address.getId()
                + ",\"shippingCode\":\"STANDARD\",\"shippingPrice\":0,\"shippingCost\":0,"
                + "\"items\":[{\"productId\":" + product.getId() + ",\"quantity\":2}]}";

        String response = mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(80.00))
                .andExpect(jsonPath("$.shippingCost").value(18.90))
                .andExpect(jsonPath("$.total").value(98.90))
                .andExpect(jsonPath("$.shipping.code").value("STANDARD"))
                .andExpect(jsonPath("$.shipping.name").value("Entrega padrão"))
                .andExpect(jsonPath("$.shipping.estimatedDays").value(8))
                .andReturn().getResponse().getContentAsString();
        Long orderId = Long.valueOf(response.replaceAll(".*\\\"id\\\":([0-9]+).*", "$1"));

        FixedShippingProvider changedProvider = new FixedShippingProvider(new BigDecimal("99.00"), 20);
        ShippingProvider.Option changed = changedProvider.quote(new ShippingProvider.Request(
                address.getZipCode(), java.util.List.of())).get(0);
        assertThat(changed.price()).isEqualByComparingTo("99.00");

        var persisted = orderRepository.findById(orderId).orElseThrow();
        assertThat(persisted.getShippingCode()).isEqualTo("STANDARD");
        assertThat(persisted.getShippingName()).isEqualTo("Entrega padrão");
        assertThat(persisted.getShippingCost()).isEqualByComparingTo("18.90");
        assertThat(persisted.getShippingEstimatedDays()).isEqualTo(8);
        assertThat(persisted.getTotal()).isEqualByComparingTo("98.90");
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity())
                .isEqualTo(98);
    }

    @Test
    void cjShippingCodeFromQuoteSurvivesRequotePriceDeadlineAndCaseChanges() throws Exception {
        User user = user("cj-shipping-stable@example.com");
        Address address = address(user, "Rua", "10");
        Product product = cjProduct("Produto CJ frete", "produto-cj-frete", "100.00");
        ProductVariant variant = variant(product, "CJ-VID-STABLE", "SKU-STABLE", "Preto",
                "10.00", "100", "10", "10", "10");
        when(commerceService.freight(anyString(), eq("BR"), anyString(), anyList()))
                .thenReturn(new CjFreightResponse(java.util.List.of(new CjFreightResponse.Option(
                        "CJPacket Postal", "7-12", new BigDecimal("5.00"), null, null, null))))
                .thenReturn(new CjFreightResponse(java.util.List.of(new CjFreightResponse.Option(
                        "CJPacket Postal", "15-20", new BigDecimal("9.00"), null, null, null))));

        String quoted = quote(user, product.getId(), variant.getId());
        String code = quoted.replaceFirst("(?s).*?\"code\":\"([^\"]+)\".*", "$1");
        assertThat(code).startsWith("CJ-CN-");
        String body = "{\"addressId\":" + address.getId() + ",\"shippingCode\":\""
                + code.toLowerCase(java.util.Locale.ROOT) + "\",\"shippingPrice\":0,"
                + "\"shippingCost\":0,\"items\":[{\"productId\":" + product.getId()
                + ",\"variantId\":" + variant.getId() + ",\"quantity\":1}]}";

        mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shipping.code").value(code))
                .andExpect(jsonPath("$.shippingCost").value(49.50))
                .andExpect(jsonPath("$.shipping.estimatedDays").value(20))
                .andExpect(jsonPath("$.items[0].productVariantId").value(variant.getId()));
        assertThat(orderRepository.findAll()).hasSize(1);
        assertThat(orderRepository.findAll().get(0).getShippingCost()).isEqualByComparingTo("49.50");
    }

    @Test
    void tamperedOrDisappearedCjShippingMethodIsRejected() throws Exception {
        User user = user("cj-shipping-invalid@example.com");
        Address address = address(user, "Rua", "10");
        Product product = cjProduct("Produto CJ inválido", "produto-cj-invalido", "100.00");
        ProductVariant variant = variant(product, "CJ-VID-INVALID", "SKU-INVALID", "Preto",
                "10.00", "100", "10", "10", "10");

        mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(address, product, variant, "CJ-CN-000000000000")))
                .andExpect(status().isBadRequest());

        String quoted = quote(user, product.getId(), variant.getId());
        String code = quoted.replaceFirst("(?s).*?\"code\":\"([^\"]+)\".*", "$1");
        reset(commerceService);
        when(commerceService.freight(anyString(), eq("BR"), anyString(), anyList()))
                .thenReturn(new CjFreightResponse(java.util.List.of(new CjFreightResponse.Option(
                        "DHL Official", "3-5", new BigDecimal("20.00"), null, null, null))));

        mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(address, product, variant, code)))
                .andExpect(status().isBadRequest());
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void cjRequoteTechnicalFailureNeverCreatesOrderWithStaleFreight() throws Exception {
        User user = user("cj-shipping-failure@example.com");
        Address address = address(user, "Rua", "10");
        Product product = cjProduct("Produto CJ falha", "produto-cj-falha", "100.00");
        ProductVariant variant = variant(product, "CJ-VID-FAIL", "SKU-FAIL", "Preto",
                "10.00", "100", "10", "10", "10");
        String quoted = quote(user, product.getId(), variant.getId());
        String code = quoted.replaceFirst("(?s).*?\"code\":\"([^\"]+)\".*", "$1");
        reset(commerceService);
        when(commerceService.freight(anyString(), eq("BR"), anyString(), anyList()))
                .thenThrow(new com.garage.garageapi.integration.cj.exception.CjIntegrationException(
                        "Falha temporária", com.garage.garageapi.integration.cj.exception.CjIntegrationException.Reason.UPSTREAM));

        mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(address, product, variant, code)))
                .andExpect(status().isBadGateway());
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void buyingExactAvailableStockLeavesZeroAndZeroStockCannotBeBought() throws Exception {
        User user = user("stock@example.com");
        Address address = address(user, "Rua", "10");
        Product product = product("Última unidade", "ultima-unidade", "50.00", true, 3);

        mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(address.getId(), item(product.getId(), 3))))
                .andExpect(status().isCreated());
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isZero();

        mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(address.getId(), item(product.getId(), 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Estoque insuficiente para o produto Última unidade."));
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isZero();
        assertThat(productRepository.findById(product.getId()).orElseThrow().getActive()).isTrue();
    }

    @Test
    void insufficientStockRollsBackEveryProductAndDoesNotCreateOrder() throws Exception {
        User user = user("rollback@example.com");
        Address address = address(user, "Rua", "10");
        Product enough = product("Disponível", "disponivel", "10.00", true, 10);
        Product insufficient = product("Limitado", "limitado", "20.00", true, 1);

        mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(address.getId(), item(enough.getId(), 2),
                                item(insufficient.getId(), 5))))
                .andExpect(status().isConflict());

        assertThat(orderRepository.count()).isZero();
        assertThat(productRepository.findById(enough.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
        assertThat(productRepository.findById(insufficient.getId()).orElseThrow().getStockQuantity()).isEqualTo(1);
    }

    @Test
    void repeatedProductIsGroupedBeforeStockValidationAndCreatesSingleSnapshot() throws Exception {
        User user = user("duplicate@example.com");
        Address address = address(user, "Rua", "10");
        Product product = product("Pretinho", "pretinho", "10.00", true, 7);

        mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(address.getId(), item(product.getId(), 3), item(product.getId(), 4))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(7))
                .andExpect(jsonPath("$.items[0].subtotal").value(70.00));
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isZero();
    }

    @Test
    void repeatedProductWhoseSumExceedsStockIsRejectedWithoutNegativeStock() throws Exception {
        User user = user("duplicate-fail@example.com");
        Address address = address(user, "Rua", "10");
        Product product = product("Pretinho", "pretinho", "10.00", true, 6);

        mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(address.getId(), item(product.getId(), 3), item(product.getId(), 4))))
                .andExpect(status().isConflict());
        assertThat(orderRepository.count()).isZero();
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(6);
    }

    @Test
    void productLookupUsesDatabaseWriteLock() throws Exception {
        Lock lock = ProductRepository.class
                .getMethod("findAllByIdInOrderByIdWithLock", java.util.List.class)
                .getAnnotation(Lock.class);
        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void invalidQuantitiesAndEmptyItemsAreRejected() throws Exception {
        User user = user("user@example.com");
        Address address = address(user, "Rua", "10");
        Product product = product("Produto", "produto", "10.00", true);

        assertBadRequest(user, request(address.getId(), item(product.getId(), 0)));
        assertBadRequest(user, request(address.getId(), item(product.getId(), -1)));
        assertBadRequest(user, "{\"addressId\":" + address.getId() + ",\"items\":[]}");
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void missingOrInactiveProductRejectsWholeTransaction() throws Exception {
        User user = user("user@example.com");
        Address address = address(user, "Rua", "10");
        Product valid = product("Ativo", "ativo", "15.00", true);
        Product inactive = product("Inativo", "inativo", "20.00", false);

        mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(address.getId(), item(999999L, 1))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(address.getId(), item(inactive.getId(), 1))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(address.getId(), item(valid.getId(), 1), item(999999L, 1))))
                .andExpect(status().isNotFound());

        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void addressMustBelongToAuthenticatedUser() throws Exception {
        User owner = user("owner@example.com");
        User attacker = user("attacker@example.com");
        Address address = address(owner, "Segredo", "99");
        Product product = product("Produto", "produto", "10.00", true);

        mockMvc.perform(post("/api/orders").header("Authorization", bearer(attacker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(address.getId(), item(product.getId(), 1))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/orders").header("Authorization", bearer(attacker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(999999L, item(product.getId(), 1))))
                .andExpect(status().isNotFound());
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void listAndDetailAreIsolatedByAuthenticatedUser() throws Exception {
        User userA = user("a@example.com");
        User userB = user("b@example.com");
        Product product = product("Produto", "produto", "10.00", true);
        Long orderA = createOrder(userA, address(userA, "Rua A", "1"), product);
        Long orderB = createOrder(userB, address(userB, "Rua B", "2"), product);

        mockMvc.perform(get("/api/orders").header("Authorization", bearer(userA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(orderA));
        mockMvc.perform(get("/api/orders/{id}", orderA).header("Authorization", bearer(userA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(orderA));
        mockMvc.perform(get("/api/orders/{id}", orderB).header("Authorization", bearer(userA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void addressAndProductSnapshotsSurviveLaterChanges() throws Exception {
        User user = user("user@example.com");
        Address address = address(user, "Rua Antiga", "100");
        Product product = product("Nome Antigo", "slug-antigo", "89.90", true);
        Long orderId = createOrder(user, address, product);

        address.update("Casa", "Outro Nome", "11111111", "Rua Nova", "200", null,
                "Outro Bairro", "Outra Cidade", "PR");
        addressRepository.saveAndFlush(address);
        product.update("Nome Novo", "slug-novo", null, null, new BigDecimal("99.90"), null,
                "Categoria", null, true, product.getProductType());
        productRepository.saveAndFlush(product);

        mockMvc.perform(get("/api/orders/{id}", orderId).header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shippingAddress.recipientName").value("Michael"))
                .andExpect(jsonPath("$.shippingAddress.street").value("Rua Antiga"))
                .andExpect(jsonPath("$.shippingAddress.number").value("100"))
                .andExpect(jsonPath("$.items[0].productName").value("Nome Antigo"))
                .andExpect(jsonPath("$.items[0].productSlug").value("slug-antigo"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(89.90));
    }

    @Test
    void cjProductWithValidVariantPersistsImmutableSupplierSnapshot() throws Exception {
        User user = user("cj-variant@example.com");
        Address address = address(user, "Rua CJ", "10");
        Product product = cjProduct("Produto CJ", "produto-cj", "129.90");
        ProductVariant variant = variant(product, "cj-variant-1", "CJ-SKU-BLACK", "Preto",
                "12.3456", "420", "120", "80", "60");

        String response = mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(address.getId(), item(product.getId(), variant.getId(), 2))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].productVariantId").value(variant.getId()))
                .andExpect(jsonPath("$.items[0].variantName").value("Preto"))
                .andExpect(jsonPath("$.items[0].fulfillmentType").value("DROPSHIPPING"))
                .andExpect(jsonPath("$.shipping.provider").value("CJ"))
                .andExpect(jsonPath("$.shipping.providerCurrency").value("USD"))
                .andExpect(jsonPath("$.shipping.providerAmount").value(5.00))
                .andExpect(jsonPath("$.shipping.legs[0].originCountry").value("CN"))
                .andReturn().getResponse().getContentAsString();
        Long orderId = Long.valueOf(response.replaceAll(".*\\\"id\\\":([0-9]+).*", "$1"));

        var snapshot = orderItemRepository.findAll().stream()
                .filter(item -> item.getProductId().equals(product.getId())).findFirst().orElseThrow();
        assertThat(snapshot.getSupplier()).isEqualTo("CJ");
        assertThat(snapshot.getSupplierProductId()).isEqualTo("cj-produto-cj");
        assertThat(snapshot.getSupplierVariantId()).isEqualTo("cj-variant-1");
        assertThat(snapshot.getSupplierSku()).isEqualTo("CJ-SKU-BLACK");
        assertThat(snapshot.getSupplierCost()).isEqualByComparingTo("12.3456");
        assertThat(snapshot.getSupplierCostCurrency()).isEqualTo("USD");
        assertThat(snapshot.getWeightGrams()).isEqualByComparingTo("420");
        assertThat(snapshot.getLengthMm()).isEqualByComparingTo("120");
        assertThat(snapshot.getWidthMm()).isEqualByComparingTo("80");
        assertThat(snapshot.getHeightMm()).isEqualByComparingTo("60");
        var savedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(savedOrder.getShippingProvider()).isEqualTo("CJ");
        assertThat(savedOrder.getShippingProviderCurrency()).isEqualTo("USD");
        assertThat(savedOrder.getShippingProviderAmount()).isEqualByComparingTo("5.00");
        assertThat(savedOrder.getShippingLegs()).hasSize(1);
        assertThat(savedOrder.getShippingLegs().get(0).supplierVariantIds())
                .containsExactly("cj-variant-1");
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isZero();

        variant.updateSupplierData("CJ", "cj-variant-1", "cj-produto-cj", "CHANGED",
                "Nome alterado", Map.of("option1", "Azul"), new BigDecimal("99.0000"),
                "USD", null, new BigDecimal("999"), null, null, null);
        productVariantRepository.saveAndFlush(variant);

        var unchanged = orderItemRepository.findById(snapshot.getId()).orElseThrow();
        assertThat(unchanged.getSupplierSku()).isEqualTo("CJ-SKU-BLACK");
        assertThat(unchanged.getVariantName()).isEqualTo("Preto");
        assertThat(unchanged.getSupplierCost()).isEqualByComparingTo("12.3456");
        assertThat(unchanged.getWeightGrams()).isEqualByComparingTo("420");
    }

    @Test
    void productWithVariantsRequiresExistingActiveVariantFromSameProduct() throws Exception {
        User user = user("variant-validation@example.com");
        Address address = address(user, "Rua", "10");
        Product first = cjProduct("Primeiro CJ", "primeiro-cj", "10.00");
        Product second = cjProduct("Segundo CJ", "segundo-cj", "20.00");
        ProductVariant firstVariant = variant(first, "variant-first", "FIRST", "Primeira",
                "1.00", "10", "1", "2", "3");
        ProductVariant secondVariant = variant(second, "variant-second", "SECOND", "Segunda",
                "2.00", "20", "4", "5", "6");

        mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(address.getId(), item(first.getId(), 999999L, 1))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(address.getId(), item(first.getId(), secondVariant.getId(), 1))))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(address.getId(), item(first.getId(), 1))))
                .andExpect(status().isConflict());

        firstVariant.setActive(false);
        productVariantRepository.saveAndFlush(firstVariant);
        mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(address.getId(), item(first.getId(), firstVariant.getId(), 1))))
                .andExpect(status().isConflict());
        assertThat(orderRepository.count()).isZero();
    }

    private Long createOrder(User user, Address address, Product product) throws Exception {
        String response = mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(address.getId(), item(product.getId(), 1))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.valueOf(response.replaceAll(".*\\\"id\\\":([0-9]+).*", "$1"));
    }

    private void assertBadRequest(User user, String body) throws Exception {
        mockMvc.perform(post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    private void cleanDatabase() {
        orderRepository.deleteAll();
        addressRepository.deleteAll();
        productVariantRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User user(String email) {
        return userRepository.save(User.local("Usuário", email, passwordEncoder.encode("strongPass123")));
    }

    private Address address(User user, String street, String number) {
        return addressRepository.save(new Address(user, "Casa", "Michael", "89229040", street,
                number, null, "Centro", "Joinville", "SC", true));
    }

    private Product product(String name, String slug, String price, boolean active) {
        return product(name, slug, price, active, 100);
    }

    private Product product(String name, String slug, String price, boolean active, int stockQuantity) {
        return productRepository.save(new Product(name, slug, null, null, new BigDecimal(price), null,
                "Categoria", stockQuantity, null, active));
    }

    private Product cjProduct(String name, String slug, String price) {
        Product product = new Product(name, slug, null, null, new BigDecimal(price), null,
                "Categoria", 0, null, true);
        product.updateAdmin(name, slug, null, null, new BigDecimal(price), null,
                new BigDecimal("55.00"), "Categoria", null, true, product.getProductType(),
                "SKU-" + slug.toUpperCase());
        product.linkSupplier("CJ", "cj-" + slug, new BigDecimal("10.0000"),
                new BigDecimal("5.500000"), Instant.now());
        product.configureFulfillment(FulfillmentType.DROPSHIPPING);
        return productRepository.save(product);
    }

    private ProductVariant variant(Product product, String supplierVariantId, String sku,
                                   String name, String cost, String weight, String length,
                                   String width, String height) {
        return productVariantRepository.save(new ProductVariant(product, "CJ", supplierVariantId,
                product.getSupplierProductId(), sku, name, Map.of("option1", name),
                new BigDecimal(cost), "USD", null, new BigDecimal(weight),
                new BigDecimal(length), new BigDecimal(width), new BigDecimal(height)));
    }

    private String quote(User user, Long productId, Long variantId) throws Exception {
        return mockMvc.perform(post("/api/shipping/quote").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"zipCode\":\"89229040\",\"items\":[{\"productId\":"
                                + productId + ",\"variantId\":" + variantId
                                + ",\"quantity\":1}]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String orderBody(Address address, Product product, ProductVariant variant, String code) {
        return "{\"addressId\":" + address.getId() + ",\"shippingCode\":\"" + code
                + "\",\"items\":[{\"productId\":" + product.getId() + ",\"variantId\":"
                + variant.getId() + ",\"quantity\":1}]}";
    }

    private String bearer(User user) { return "Bearer " + jwtService.issue(user).value(); }

    private String item(Long productId, int quantity) {
        return "{\"productId\":" + productId + ",\"quantity\":" + quantity + "}";
    }

    private String item(Long productId, Long variantId, int quantity) {
        return "{\"productId\":" + productId + ",\"variantId\":" + variantId
                + ",\"quantity\":" + quantity + "}";
    }

    private String request(Long addressId, String... items) {
        return "{\"addressId\":" + addressId + ",\"items\":[" + String.join(",", items) + "]}";
    }
}
