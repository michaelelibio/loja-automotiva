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
import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.repository.ProductRepository;
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

    public OrderService(OrderRepository orderRepository, AddressRepository addressRepository,
                        ProductRepository productRepository, UserService userService,
                        ShippingService shippingService, StockService stockService,
                        @Value("${app.order.expiration:PT24H}") Duration orderExpiration) {
        this.orderRepository = orderRepository;
        this.addressRepository = addressRepository;
        this.productRepository = productRepository;
        this.userService = userService;
        this.shippingService = shippingService;
        this.stockService = stockService;
        this.orderExpiration = orderExpiration;
    }

    @Transactional
    public OrderResponse create(Jwt jwt, CreateOrderRequest request) {
        User user = userService.findCurrentUser(jwt);
        Address address = addressRepository.findByIdAndUserId(request.addressId(), user.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Endereço não encontrado: " + request.addressId()));

        Map<Long, Integer> requestedQuantities = aggregateQuantities(request.items());
        List<Long> productIds = new ArrayList<>(requestedQuantities.keySet());
        List<Product> products = productRepository.findAllByIdInOrderByIdWithLock(productIds);

        if (products.size() != productIds.size()) {
            Long missingId = productIds.stream()
                    .filter(id -> products.stream().noneMatch(product -> product.getId().equals(id)))
                    .findFirst().orElseThrow();
            throw new ResourceNotFoundException("Produto não encontrado: " + missingId);
        }

        List<ItemSnapshot> snapshots = new ArrayList<>();
        BigDecimal subtotal = ZERO_MONEY;
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
            BigDecimal unitPrice = money(product.getPrice());
            BigDecimal itemSubtotal = money(unitPrice.multiply(BigDecimal.valueOf(requestedQuantity)));
            snapshots.add(new ItemSnapshot(product, requestedQuantity, unitPrice, itemSubtotal));
            subtotal = subtotal.add(itemSubtotal);
        }
        subtotal = money(subtotal);

        List<ShippingProvider.Item> shippingItems = snapshots.stream()
                .map(snapshot -> shippingService.item(snapshot.product(), snapshot.quantity()))
                .toList();
        ShippingProvider.Option shipping = shippingService.select(address.getZipCode(),
                shippingItems, request.shippingCode());

        Order order = new Order(user, address, subtotal, shipping, orderExpiration);
        snapshots.forEach(snapshot -> order.addItem(new OrderItem(order, snapshot.product(),
                snapshot.quantity(), snapshot.unitPrice(), snapshot.subtotal())));
        orderRepository.saveAndFlush(order);
        snapshots.stream().filter(snapshot -> snapshot.product().requiresLocalStock())
                .forEach(snapshot -> stockService.recordSale(
                        snapshot.product(), snapshot.quantity(), order.getId()));
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

    private Map<Long, Integer> aggregateQuantities(List<CreateOrderItemRequest> items) {
        Map<Long, Integer> quantities = new TreeMap<>();
        for (CreateOrderItemRequest item : items) {
            quantities.merge(item.productId(), item.quantity(), Math::addExact);
        }
        return quantities;
    }

    private record ItemSnapshot(Product product, int quantity, BigDecimal unitPrice,
                                BigDecimal subtotal) { }
}
