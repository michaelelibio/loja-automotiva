package com.garage.garageapi.order.fulfillment;

import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.address.repository.AddressRepository;
import com.garage.garageapi.integration.cj.dto.CjCreateOrderRequest;
import com.garage.garageapi.integration.cj.dto.CjCreateOrderResponse;
import com.garage.garageapi.integration.cj.dto.CjOrderLookupResponse;
import com.garage.garageapi.integration.cj.exception.CjIntegrationException;
import com.garage.garageapi.integration.cj.service.CjCommerceService;
import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.entity.OrderItem;
import com.garage.garageapi.order.repository.OrderItemRepository;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.product.entity.FulfillmentType;
import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.entity.ProductVariant;
import com.garage.garageapi.product.repository.ProductRepository;
import com.garage.garageapi.product.repository.ProductVariantRepository;
import com.garage.garageapi.shipping.provider.ShippingProvider;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
class CjFulfillmentIntegrationTests {
    @Autowired CjFulfillmentService service;
    @Autowired OrderFulfillmentInitializer initializer;
    @Autowired OrderFulfillmentRepository fulfillmentRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderItemRepository orderItemRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired AddressRepository addressRepository;
    @Autowired UserRepository userRepository;
    @MockitoBean CjCommerceService commerceService;

    @BeforeEach void before() {
        clean();
        reset(commerceService);
        when(commerceService.findOrder(anyString())).thenReturn(java.util.Optional.empty());
    }
    @AfterEach void after() { clean(); }

    @Test
    void localOrderIsNotRequiredAndNeverCallsCj() {
        Fixture fixture = fixture(false, true, false);
        pay(fixture.order());
        service.fulfill(fixture.order().getId());
        assertThat(fulfillment().getStatus()).isEqualTo(FulfillmentStatus.NOT_REQUIRED);
        verifyNoInteractions(commerceService);
    }

    @Test
    void pendingCjOrderDoesNotCreateBeforePayment() {
        fixture(true, true, false);
        service.fulfill(orderRepository.findAll().get(0).getId());
        assertThat(fulfillment().getStatus()).isEqualTo(FulfillmentStatus.PENDING);
        verifyNoInteractions(commerceService);
    }

    @Test
    void paidCjOrderCreatesOnceFromSnapshotsAndRepeatedDeliveryIsIdempotent() {
        Fixture fixture = fixture(true, true, true);
        when(commerceService.createOrder(any())).thenReturn(
                new CjCreateOrderResponse("CJ-123", "SHIP-123", "ignored", "CREATED"));
        pay(fixture.order());

        service.fulfill(fixture.order().getId());
        service.fulfill(fixture.order().getId());

        OrderFulfillment fulfillment = fulfillment();
        assertThat(fulfillment.getStatus()).isEqualTo(FulfillmentStatus.CREATED);
        assertThat(fulfillment.getSupplierOrderId()).isEqualTo("CJ-123");
        assertThat(fulfillment.getSupplierShipmentOrderId()).isEqualTo("SHIP-123");
        assertThat(fulfillment.getAttemptCount()).isOne();
        ArgumentCaptor<CjCreateOrderRequest> captor = ArgumentCaptor.forClass(CjCreateOrderRequest.class);
        verify(commerceService, times(1)).createOrder(captor.capture());
        CjCreateOrderRequest request = captor.getValue();
        assertThat(request.orderNumber()).isEqualTo("INGARAGE-" + fixture.order().getId());
        assertThat(request.payType()).isEqualTo(3);
        assertThat(request.logisticName()).isEqualTo("CJPacket");
        assertThat(request.fromCountryCode()).isEqualTo("CN");
        assertThat(request.products()).extracting(CjCreateOrderRequest.Product::vid)
                .containsExactly("CJ-VARIANT");
    }

    @Test
    void concurrentAttemptsClaimOnlyOnceWithoutHoldingDatabaseLockDuringHttp() throws Exception {
        Fixture fixture = fixture(true, true, false);
        pay(fixture.order());
        CountDownLatch enteredHttp = new CountDownLatch(1);
        CountDownLatch releaseHttp = new CountDownLatch(1);
        when(commerceService.createOrder(any())).thenAnswer(invocation -> {
            enteredHttp.countDown();
            assertThat(releaseHttp.await(5, TimeUnit.SECONDS)).isTrue();
            return new CjCreateOrderResponse("CJ-CONCURRENT", null, null, "CREATED");
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> service.fulfill(fixture.order().getId()));
            assertThat(enteredHttp.await(5, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> service.fulfill(fixture.order().getId()));
            second.get(5, TimeUnit.SECONDS);
            releaseHttp.countDown();
            first.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertThat(fulfillment().getStatus()).isEqualTo(FulfillmentStatus.CREATED);
        verify(commerceService, times(1)).createOrder(any());
    }

    @Test
    void compositeOrderSendsOnlyCjItems() {
        Fixture fixture = fixture(true, true, true);
        when(commerceService.createOrder(any())).thenReturn(
                new CjCreateOrderResponse("CJ-MIX", null, null, "CREATED"));
        pay(fixture.order());
        service.fulfill(fixture.order().getId());

        ArgumentCaptor<CjCreateOrderRequest> captor = ArgumentCaptor.forClass(CjCreateOrderRequest.class);
        verify(commerceService).createOrder(captor.capture());
        assertThat(captor.getValue().products()).hasSize(1);
        assertThat(captor.getValue().products().get(0).vid()).isEqualTo("CJ-VARIANT");
    }

    @Test
    void externalFailureKeepsOrderPaidAndControlledRetryCanCreate() {
        Fixture fixture = fixture(true, true, false);
        when(commerceService.createOrder(any()))
                .thenThrow(new CjIntegrationException("internal upstream detail",
                        CjIntegrationException.Reason.UPSTREAM))
                .thenReturn(new CjCreateOrderResponse("CJ-RETRY", null, null, "CREATED"));
        pay(fixture.order());

        service.fulfill(fixture.order().getId());
        assertThat(fulfillment().getStatus()).isEqualTo(FulfillmentStatus.FAILED);
        assertThat(fulfillment().getLastError()).isEqualTo("Falha temporária ao criar pedido no fornecedor");
        assertThat(orderRepository.findById(fixture.order().getId()).orElseThrow().getStatus().name())
                .isEqualTo("PAID");

        service.fulfill(fixture.order().getId());
        assertThat(fulfillment().getStatus()).isEqualTo(FulfillmentStatus.CREATED);
        assertThat(fulfillment().getAttemptCount()).isEqualTo(2);
        verify(commerceService, times(2)).createOrder(any());
    }

    @Test
    void failedFulfillmentReconcilesExistingOrderWithoutCreatingAgain() {
        Fixture fixture = fixture(true, true, false);
        pay(fixture.order());
        when(commerceService.createOrder(any())).thenThrow(new CjIntegrationException("timeout",
                CjIntegrationException.Reason.UPSTREAM));
        service.fulfill(fixture.order().getId());
        assertThat(fulfillment().getStatus()).isEqualTo(FulfillmentStatus.FAILED);
        clearInvocations(commerceService);
        when(commerceService.findOrder("INGARAGE-" + fixture.order().getId())).thenReturn(Optional.of(
                new CjOrderLookupResponse("CJ-RECOVERED", "SHIP-RECOVERED",
                        "INGARAGE-" + fixture.order().getId(), "CREATED")));

        service.fulfill(fixture.order().getId());

        assertThat(fulfillment().getStatus()).isEqualTo(FulfillmentStatus.CREATED);
        assertThat(fulfillment().getSupplierOrderId()).isEqualTo("CJ-RECOVERED");
        assertThat(fulfillment().getAttemptCount()).isOne();
        verify(commerceService, never()).createOrder(any());
    }

    @Test
    void createdReconciliationIsIdempotentWithoutQuery() {
        Fixture fixture = fixture(true, true, false);
        pay(fixture.order());
        when(commerceService.createOrder(any())).thenReturn(
                new CjCreateOrderResponse("CJ-CREATED", null, null, "CREATED"));
        service.fulfill(fixture.order().getId());
        clearInvocations(commerceService);

        assertThat(service.reconcile(fixture.order().getId()))
                .isEqualTo(CjFulfillmentService.ReconciliationResult.FOUND);
        verifyNoInteractions(commerceService);
    }

    @Test
    void reconciliationNotFoundIsNotTechnicalFailureAndDoesNotCountAttempt() {
        Fixture fixture = fixture(true, true, false);
        pay(fixture.order());

        assertThat(service.reconcile(fixture.order().getId()))
                .isEqualTo(CjFulfillmentService.ReconciliationResult.NOT_FOUND);
        assertThat(fulfillment().getStatus()).isEqualTo(FulfillmentStatus.PENDING);
        assertThat(fulfillment().getAttemptCount()).isZero();
        verify(commerceService, never()).createOrder(any());
    }

    @Test
    void divergentReturnedOrderNumberDoesNotReconcileOrCreate() {
        Fixture fixture = fixture(true, true, false);
        pay(fixture.order());
        when(commerceService.findOrder(anyString())).thenReturn(Optional.of(
                new CjOrderLookupResponse("CJ-WRONG", null, "INGARAGE-OTHER", "CREATED")));

        service.fulfill(fixture.order().getId());

        assertThat(fulfillment().getStatus()).isEqualTo(FulfillmentStatus.FAILED);
        assertThat(fulfillment().getAttemptCount()).isZero();
        verify(commerceService, never()).createOrder(any());
    }

    @Test
    void technicalLookupFailureDoesNotCreateBlindlyAndKeepsPaymentPaid() {
        Fixture fixture = fixture(true, true, false);
        pay(fixture.order());
        when(commerceService.findOrder(anyString())).thenThrow(new CjIntegrationException("timeout",
                CjIntegrationException.Reason.UPSTREAM));

        service.fulfill(fixture.order().getId());

        assertThat(fulfillment().getStatus()).isEqualTo(FulfillmentStatus.FAILED);
        assertThat(fulfillment().getAttemptCount()).isZero();
        assertThat(orderRepository.findById(fixture.order().getId()).orElseThrow().getStatus().name())
                .isEqualTo("PAID");
        verify(commerceService, never()).createOrder(any());
    }

    @Test
    void duplicateCreationReconcilesOnlyWhenExactOrderCanBeProven() {
        Fixture fixture = fixture(true, true, false);
        pay(fixture.order());
        when(commerceService.findOrder(anyString())).thenReturn(Optional.empty(), Optional.of(
                new CjOrderLookupResponse("CJ-DUPLICATE", null,
                        "INGARAGE-" + fixture.order().getId(), "CREATED")));
        when(commerceService.createOrder(any())).thenThrow(new CjIntegrationException("duplicate",
                CjIntegrationException.Reason.CONFLICT));

        service.fulfill(fixture.order().getId());

        assertThat(fulfillment().getStatus()).isEqualTo(FulfillmentStatus.CREATED);
        assertThat(fulfillment().getSupplierOrderId()).isEqualTo("CJ-DUPLICATE");
        verify(commerceService, times(1)).createOrder(any());
        verify(commerceService, times(2)).findOrder(anyString());
    }

    @Test
    void duplicateCreationWithoutReconciliationProofRemainsFailed() {
        Fixture fixture = fixture(true, true, false);
        pay(fixture.order());
        when(commerceService.createOrder(any())).thenThrow(new CjIntegrationException("duplicate",
                CjIntegrationException.Reason.CONFLICT));

        service.fulfill(fixture.order().getId());

        assertThat(fulfillment().getStatus()).isEqualTo(FulfillmentStatus.FAILED);
        assertThat(fulfillment().getSupplierOrderId()).isNull();
        verify(commerceService, times(2)).findOrder(anyString());
    }

    @Test
    void missingVariantOrLogisticsFailsBeforeExternalCall() {
        Fixture missingVariant = fixture(true, false, false);
        pay(missingVariant.order());
        service.fulfill(missingVariant.order().getId());
        assertThat(fulfillment().getStatus()).isEqualTo(FulfillmentStatus.FAILED);
        assertThat(fulfillment().getLastError()).contains("Variante CJ ausente");
        verifyNoInteractions(commerceService);
    }

    @Test
    void missingCjLogisticsFailsBeforeExternalCall() {
        Fixture fixture = fixture(true, true, false, "Joinville", true);
        pay(fixture.order());
        service.fulfill(fixture.order().getId());
        assertThat(fulfillment().getStatus()).isEqualTo(FulfillmentStatus.FAILED);
        assertThat(fulfillment().getLastError()).contains("logístico CJ");
        verifyNoInteractions(commerceService);
    }

    @Test
    void invalidAddressFailsBeforeExternalCall() {
        Fixture fixture = fixture(true, true, false, "");
        pay(fixture.order());
        service.fulfill(fixture.order().getId());
        assertThat(fulfillment().getStatus()).isEqualTo(FulfillmentStatus.FAILED);
        assertThat(fulfillment().getLastError()).contains("Cidade ausente");
        verifyNoInteractions(commerceService);
    }

    private Fixture fixture(boolean cj, boolean withVariant, boolean composite) {
        return fixture(cj, withVariant, composite, "Joinville");
    }

    private Fixture fixture(boolean cj, boolean withVariant, boolean composite, String city) {
        return fixture(cj, withVariant, composite, city, false);
    }

    private Fixture fixture(boolean cj, boolean withVariant, boolean composite, String city,
                            boolean missingLogistics) {
        User user = userRepository.save(User.local("Cliente", "fulfillment-" + System.nanoTime()
                + "@example.com", "hash"));
        Address address = addressRepository.save(new Address(user, "Casa", "Cliente", "89229040",
                "Rua das Flores", "10", "Apto 2", "Centro", city, "SC", true));
        Product product = product(cj ? "CJ" : "Local", cj);
        ProductVariant variant = cj && withVariant ? variant(product) : null;
        ShippingProvider.Leg cjLeg = new ShippingProvider.Leg("CJ", "CJ-CODE", "CJPacket", "CN",
                new BigDecimal("5.00"), "USD", new BigDecimal("27.50"), 10,
                List.of("CJ-VARIANT"));
        ShippingProvider.Leg localLeg = new ShippingProvider.Leg("LOCAL", "STANDARD", "Entrega padrão",
                "BR", new BigDecimal("18.90"), "BRL", new BigDecimal("18.90"), 8, List.of());
        List<ShippingProvider.Leg> legs = missingLogistics ? List.of() : cj
                ? (composite ? List.of(localLeg, cjLeg) : List.of(cjLeg)) : List.of(localLeg);
        ShippingProvider.Option option = new ShippingProvider.Option(cj ? "SHIP" : "STANDARD",
                "Entrega", cj ? new BigDecimal("27.50") : new BigDecimal("18.90"), 10,
                composite ? "COMPOSITE" : (cj ? "CJ" : "LOCAL"), new BigDecimal("5.00"),
                cj ? "USD" : "BRL", legs);
        Order order = new Order(user, address, new BigDecimal("100.00"), option, Duration.ofHours(24));
        order.addItem(new OrderItem(order, product, variant, 2, new BigDecimal("50.00"),
                new BigDecimal("100.00")));
        if (composite) {
            Product local = product("Local misto", false);
            order.addItem(new OrderItem(order, local, 1, new BigDecimal("10.00"),
                    new BigDecimal("10.00")));
        }
        orderRepository.saveAndFlush(order);
        initializer.initialize(order);
        return new Fixture(order);
    }

    private Product product(String name, boolean cj) {
        Product product = new Product(name, name.toLowerCase() + "-" + System.nanoTime(), null, null,
                new BigDecimal("50.00"), null, "Categoria", cj ? 0 : 10, null, true);
        if (cj) {
            product.configureFulfillment(FulfillmentType.DROPSHIPPING);
            product.linkSupplier("CJ", "CJ-PRODUCT", new BigDecimal("5.00"),
                    new BigDecimal("5.50"), Instant.now());
        }
        return productRepository.save(product);
    }

    private ProductVariant variant(Product product) {
        return variantRepository.save(new ProductVariant(product, "CJ", "CJ-VARIANT", "CJ-PRODUCT",
                "CJ-SKU", "Preto", Map.of("color", "black"), new BigDecimal("5.00"),
                "USD", null, new BigDecimal("100"), null, null, null));
    }

    private void pay(Order order) { order.markPaid(); orderRepository.saveAndFlush(order); }
    private OrderFulfillment fulfillment() { return fulfillmentRepository.findAll().get(0); }

    private void clean() {
        fulfillmentRepository.deleteAll();
        orderRepository.deleteAll();
        addressRepository.deleteAll();
        variantRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    private record Fixture(Order order) { }
}
