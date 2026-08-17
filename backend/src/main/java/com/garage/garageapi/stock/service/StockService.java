package com.garage.garageapi.stock.service;

import com.garage.garageapi.admin.stock.dto.AdminStockMovementPageResponse;
import com.garage.garageapi.admin.stock.dto.AdminStockMovementRequest;
import com.garage.garageapi.admin.stock.dto.AdminStockMovementResponse;
import com.garage.garageapi.admin.stock.dto.AdminStockSummaryResponse;
import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.entity.FulfillmentType;
import com.garage.garageapi.product.repository.ProductRepository;
import com.garage.garageapi.shared.exception.ResourceConflictException;
import com.garage.garageapi.shared.exception.ResourceNotFoundException;
import com.garage.garageapi.stock.entity.StockMovement;
import com.garage.garageapi.stock.entity.StockMovementType;
import com.garage.garageapi.stock.entity.StockReferenceType;
import com.garage.garageapi.stock.repository.StockMovementRepository;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class StockService {
    private final ProductRepository productRepository;
    private final StockMovementRepository movementRepository;
    private final UserService userService;

    public StockService(ProductRepository productRepository, StockMovementRepository movementRepository,
                        UserService userService) {
        this.productRepository = productRepository;
        this.movementRepository = movementRepository;
        this.userService = userService;
    }

    @Transactional
    public AdminStockMovementResponse createManual(Jwt jwt, AdminStockMovementRequest request) {
        User admin = userService.findCurrentUser(jwt);
        Product product = productRepository.findByIdWithLock(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Produto não encontrado: " + request.productId()));
        StockMovement movement = apply(product, request.type().toEntityType(), request.quantity(),
                request.reason().trim(), null, null, admin);
        return AdminStockMovementResponse.from(movementRepository.saveAndFlush(movement));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSale(Product lockedProduct, int quantity, Long orderId) {
        StockMovement movement = apply(lockedProduct, StockMovementType.SALE, quantity,
                "Baixa automática pela criação do pedido", StockReferenceType.ORDER, orderId, null);
        movementRepository.save(movement);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordInitialStock(Product product, int quantity, User performedBy) {
        if (quantity == 0) return;
        StockMovement movement = apply(product, StockMovementType.INITIAL_STOCK, quantity,
                "Estoque inicial do produto", null, null, performedBy);
        movementRepository.save(movement);
    }

    @Transactional(readOnly = true)
    public AdminStockMovementPageResponse list(Long productId, StockMovementType type,
                                               Instant dateFrom, Instant dateTo, int page, int size) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new IllegalArgumentException("dateFrom deve ser anterior ou igual a dateTo");
        }
        Specification<StockMovement> filters = (root, query, builder) -> builder.conjunction();
        if (productId != null) filters = filters.and((root, query, builder) ->
                builder.equal(root.get("product").get("id"), productId));
        if (type != null) filters = filters.and((root, query, builder) ->
                builder.equal(root.get("type"), type));
        if (dateFrom != null) filters = filters.and((root, query, builder) ->
                builder.greaterThanOrEqualTo(root.get("createdAt"), dateFrom));
        if (dateTo != null) filters = filters.and((root, query, builder) ->
                builder.lessThanOrEqualTo(root.get("createdAt"), dateTo));
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<AdminStockMovementResponse> movements = movementRepository.findAll(filters, pageable)
                .map(AdminStockMovementResponse::from);
        return AdminStockMovementPageResponse.from(movements);
    }

    @Transactional(readOnly = true)
    public AdminStockSummaryResponse summary() {
        return new AdminStockSummaryResponse(productRepository.count(),
                productRepository.sumStockQuantity(),
                productRepository.countByStockQuantityAndFulfillmentType(0, FulfillmentType.LOCAL_STOCK));
    }

    private StockMovement apply(Product product, StockMovementType type, int quantity, String reason,
                                StockReferenceType referenceType, Long referenceId, User performedBy) {
        if (!product.requiresLocalStock()) {
            throw new ResourceConflictException(
                    "Estoque local não se aplica ao produto dropshipping " + product.getName());
        }
        int previous = product.getStockQuantity();
        try {
            switch (type) {
                case INITIAL_STOCK, PURCHASE_ENTRY, MANUAL_ADJUSTMENT_IN -> product.increaseStock(quantity);
                case SALE, MANUAL_ADJUSTMENT_OUT -> product.decreaseStock(quantity);
            }
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new ResourceConflictException("Estoque insuficiente ou quantidade inválida para o produto "
                    + product.getName());
        }
        return new StockMovement(product, type, quantity, previous, product.getStockQuantity(), reason,
                referenceType, referenceId, performedBy);
    }
}
