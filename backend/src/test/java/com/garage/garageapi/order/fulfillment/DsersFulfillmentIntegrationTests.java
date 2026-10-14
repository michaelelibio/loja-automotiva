package com.garage.garageapi.order.fulfillment;

import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.address.repository.AddressRepository;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "dsers.simulation.enabled=true")
class DsersFulfillmentIntegrationTests {
    @Autowired SupplierFulfillmentService service;
    @Autowired OrderFulfillmentInitializer initializer;
    @Autowired OrderFulfillmentRepository fulfillmentRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderItemRepository orderItemRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired AddressRepository addressRepository;
    @Autowired UserRepository userRepository;

    @AfterEach void after() {
        fulfillmentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        addressRepository.deleteAll();
        variantRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void paidDsersOrderUsesVariantSnapshotAndIsIdempotent() {
        Order order = fixture(true);
        order.markPaid();
        orderRepository.saveAndFlush(order);

        service.fulfill(order.getId());
        service.fulfill(order.getId());

        OrderFulfillment fulfillment = fulfillmentRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(fulfillment.getProvider()).isEqualTo("DSERS");
        assertThat(fulfillment.getStatus()).isEqualTo(FulfillmentStatus.CREATED);
        assertThat(fulfillment.getSupplierOrderId()).isEqualTo("DSERS-SIM-INGARAGE-" + order.getId());
        assertThat(fulfillment.getAttemptCount()).isOne();
    }

    @Test
    void missingVariantMappingFailsBeforeSimulationCreatesAnOrder() {
        Order order = fixture(false);
        order.markPaid();
        orderRepository.saveAndFlush(order);

        service.fulfill(order.getId());

        OrderFulfillment fulfillment = fulfillmentRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(fulfillment.getStatus()).isEqualTo(FulfillmentStatus.FAILED);
        assertThat(fulfillment.getLastError()).contains("Variante DSers ausente");
        assertThat(fulfillment.getAttemptCount()).isZero();
    }

    private Order fixture(boolean withVariant) {
        User user = userRepository.save(User.local("Cliente", "dsers-" + System.nanoTime()
                + "@example.com", "hash"));
        Address address = addressRepository.save(new Address(user, "Casa", "Cliente", "89229040",
                "Rua das Flores", "10", null, "Centro", "Joinville", "SC", true));
        Product product = new Product("Produto DSers", "dsers-" + System.nanoTime(), null, null,
                new BigDecimal("50.00"), null, "Categoria", 0, null, true);
        product.configureFulfillment(FulfillmentType.DROPSHIPPING);
        product.linkSupplier("DSERS", "DSERS-PRODUCT", new BigDecimal("5.00"),
                new BigDecimal("5.50"), Instant.now());
        productRepository.save(product);
        ProductVariant variant = withVariant ? variantRepository.save(new ProductVariant(product,
                "DSERS", "DSERS-VARIANT", "DSERS-PRODUCT", "DSERS-SKU", "Preto",
                Map.of("color", "black"), new BigDecimal("5.00"), "USD", null,
                new BigDecimal("100"), null, null, null)) : null;
        var shipping = new ShippingProvider.Option("STANDARD", "Entrega", new BigDecimal("18.90"),
                8, "LOCAL", new BigDecimal("18.90"), "BRL", List.of());
        Order order = new Order(user, address, new BigDecimal("50.00"), shipping, Duration.ofHours(24));
        order.addItem(new OrderItem(order, product, variant, 1, new BigDecimal("50.00"),
                new BigDecimal("50.00")));
        orderRepository.saveAndFlush(order);
        initializer.initialize(order);
        return order;
    }
}
