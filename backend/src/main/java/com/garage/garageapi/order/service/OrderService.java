package com.garage.garageapi.order.service;

import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.address.repository.AddressRepository;
import com.garage.garageapi.favorite.exception.InactiveProductException;
import com.garage.garageapi.order.dto.CreateOrderItemRequest;
import com.garage.garageapi.order.dto.CreateOrderRequest;
import com.garage.garageapi.order.dto.OrderResponse;
import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.entity.OrderItem;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.order.fulfillment.OrderFulfillmentInitializer;
import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.entity.ProductVariant;
import com.garage.garageapi.product.repository.ProductRepository;
import com.garage.garageapi.product.repository.ProductVariantRepository;
import com.garage.garageapi.shared.exception.ResourceNotFoundException;
import com.garage.garageapi.shared.exception.ResourceConflictException;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.service.UserService;
import com.garage.garageapi.shipping.provider.ShippingProvider;
import com.garage.garageapi.shipping.service.ShippingService;
import com.garage.garageapi.stock.service.StockService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class OrderService {
    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");

    private final OrderRepository orderRepository;
    private final AddressRepository addressRepository;
    private final ProductRepository productRepository;
    private final UserService userService;
    private final Duration orderExpiration;
    private final ShippingService shippingService;
    private final StockService stockService;
    private final ProductVariantRepository productVariantRepository;
    private final OrderFulfillmentInitializer fulfillmentInitializer;

    public OrderService(OrderRepository orderRepository, AddressRepository addressRepository,
                        ProductRepository productRepository, UserService userService,
                        ShippingService shippingService, StockService stockService,
                        ProductVariantRepository productVariantRepository,
                        OrderFulfillmentInitializer fulfillmentInitializer,
                        @Value("${app.order.expiration:PT24H}") Duration orderExpiration) {
        this.orderRepository = orderRepository;
        this.addressRepository = addressRepository;
        this.productRepository = productRepository;
        this.userService = userService;
        this.shippingService = shippingService;
        this.stockService = stockService;
        this.productVariantRepository = productVariantRepository;
        this.fulfillmentInitializer = fulfillmentInitializer;
        this.orderExpiration = orderExpiration;
    }

    @Transactional
    public OrderResponse create(Jwt jwt, CreateOrderRequest request) {
        User user = userService.findCurrentUser(jwt);
        Address address = addressRepository.findByIdAndUserId(request.addressId(), user.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Endereço não encontrado: " + request.addressId()));

        Map<PurchaseKey, Integer> requestedLines = aggregateLines(request.items());
        Map<Long, Integer> requestedQuantities = aggregateProductQuantities(requestedLines);
        List<Long> productIds = new ArrayList<>(requestedQuantities.keySet());
        List<Product> products = productRepository.findAllByIdInOrderByIdWithLock(productIds);

        if (products.size() != productIds.size()) {
            Long missingId = productIds.stream()
                    .filter(id -> products.stream().noneMatch(product -> product.getId().equals(id)))
                    .findFirst().orElseThrow();
            throw new ResourceNotFoundException("Produto não encontrado: " + missingId);
        }

        for (Product product : products) {
            int requestedQuantity = requestedQuantities.get(product.getId());
            if (!Boolean.TRUE.equals(product.getActive())) {
                throw new InactiveProductException(
                        "Produto inativo não pode ser incluído no pedido: " + product.getId());
            }
            if (!product.canFulfill(requestedQuantity)) {
                throw new ResourceConflictException(
                        "Estoque insuficiente para o produto " + product.getName() + ".");
            }
        }

        Map<Long, Product> productsById = new TreeMap<>();
        products.forEach(product -> productsById.put(product.getId(), product));
        List<ItemSnapshot> snapshots = new ArrayList<>();
        BigDecimal subtotal = ZERO_MONEY;
        for (Map.Entry<PurchaseKey, Integer> entry : requestedLines.entrySet()) {
            Product product = productsById.get(entry.getKey().productId());
            ProductVariant variant = resolveVariant(product, entry.getKey().variantId());
            BigDecimal unitPrice = money(product.getPrice());
            BigDecimal itemSubtotal = money(unitPrice.multiply(BigDecimal.valueOf(entry.getValue())));
            snapshots.add(new ItemSnapshot(product, variant, entry.getValue(), unitPrice, itemSubtotal));
            subtotal = subtotal.add(itemSubtotal);
        }
        subtotal = money(subtotal);

        List<ShippingProvider.Item> shippingItems = snapshots.stream()
                .map(snapshot -> shippingService.item(snapshot.product(), snapshot.variant(),
                        snapshot.quantity()))
                .toList();
        ShippingProvider.Option shipping = shippingService.select(address.getZipCode(),
                shippingItems, request.shippingCode());

        Order order = new Order(user, address, subtotal, shipping, orderExpiration);
        snapshots.forEach(snapshot -> order.addItem(new OrderItem(order, snapshot.product(),
                snapshot.variant(), snapshot.quantity(), snapshot.unitPrice(), snapshot.subtotal())));
        orderRepository.saveAndFlush(order);
        fulfillmentInitializer.initialize(order);
        products.stream().filter(Product::requiresLocalStock)
                .forEach(product -> stockService.recordSale(product,
                        requestedQuantities.get(product.getId()), order.getId()));
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> list(Jwt jwt) {
        User user = userService.findCurrentUser(jwt);
        return orderRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(Jwt jwt, Long id) {
        User user = userService.findCurrentUser(jwt);
        Order order = orderRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado: " + id));
        return OrderResponse.from(order);
    }

    private BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }

    private Map<PurchaseKey, Integer> aggregateLines(List<CreateOrderItemRequest> items) {
        Map<PurchaseKey, Integer> quantities = new TreeMap<>();
        for (CreateOrderItemRequest item : items) {
            quantities.merge(new PurchaseKey(item.productId(), item.variantId()),
                    item.quantity(), Math::addExact);
        }
        return quantities;
    }

    private Map<Long, Integer> aggregateProductQuantities(Map<PurchaseKey, Integer> lines) {
        Map<Long, Integer> quantities = new TreeMap<>();
        lines.forEach((key, quantity) -> quantities.merge(key.productId(), quantity, Math::addExact));
        return quantities;
    }

    private ProductVariant resolveVariant(Product product, Long requestedVariantId) {
        boolean productHasVariants = productVariantRepository.existsByProductId(product.getId());
        if (requestedVariantId == null) {
            if (productHasVariants) {
                throw new ResourceConflictException(
                        "Selecione uma variante para o produto " + product.getName() + ".");
            }
            return null;
        }

        ProductVariant variant = productVariantRepository.findById(requestedVariantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Variante não encontrada: " + requestedVariantId));
        if (!variant.getProduct().getId().equals(product.getId())) {
            throw new ResourceConflictException("A variante selecionada não pertence ao produto "
                    + product.getName() + ".");
        }
        if (!Boolean.TRUE.equals(variant.getActive())) {
            throw new ResourceConflictException("A variante selecionada não está disponível.");
        }
        return variant;
    }

    private record PurchaseKey(Long productId, Long variantId) implements Comparable<PurchaseKey> {
        @Override
        public int compareTo(PurchaseKey other) {
            int productComparison = productId.compareTo(other.productId);
            if (productComparison != 0) return productComparison;
            if (variantId == null) return other.variantId == null ? 0 : -1;
            return other.variantId == null ? 1 : variantId.compareTo(other.variantId);
        }
    }

    private record ItemSnapshot(Product product, ProductVariant variant, int quantity, BigDecimal unitPrice,
                                BigDecimal subtotal) { }
}
