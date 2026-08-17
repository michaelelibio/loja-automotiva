package com.garage.garageapi.admin.product;

import com.garage.garageapi.admin.product.dto.AdminProductRequest;
import com.garage.garageapi.admin.product.dto.AdminProductPageResponse;
import com.garage.garageapi.admin.product.dto.AdminProductResponse;
import com.garage.garageapi.admin.product.dto.AdminProductUpdateRequest;
import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.entity.ProductType;
import com.garage.garageapi.product.repository.ProductRepository;
import com.garage.garageapi.shared.exception.ResourceConflictException;
import com.garage.garageapi.shared.exception.ResourceNotFoundException;
import com.garage.garageapi.stock.service.StockService;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Locale;

@Service
public class AdminProductService {
    private final ProductRepository productRepository;
    private final StockService stockService;
    private final UserService userService;

    public AdminProductService(ProductRepository productRepository, StockService stockService,
                               UserService userService) {
        this.productRepository = productRepository;
        this.stockService = stockService;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public AdminProductPageResponse list(String search, Boolean active, String category,
                                         int page, int size) {
        String cleanSearch = cleanOptional(search);
        String cleanCategory = cleanOptional(category);
        Specification<Product> filters = (root, query, builder) -> builder.conjunction();
        if (cleanSearch != null) {
            String pattern = "%" + cleanSearch.toLowerCase(Locale.ROOT) + "%";
            filters = filters.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("name")), pattern),
                    builder.like(builder.lower(root.get("sku")), pattern)));
        }
        if (active != null) {
            filters = filters.and((root, query, builder) -> builder.equal(root.get("active"), active));
        }
        if (cleanCategory != null) {
            String normalizedCategory = cleanCategory.toLowerCase(Locale.ROOT);
            filters = filters.and((root, query, builder) ->
                    builder.equal(builder.lower(root.get("category")), normalizedCategory));
        }
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<AdminProductResponse> products = productRepository.findAll(filters, pageable)
                .map(AdminProductResponse::from);
        return AdminProductPageResponse.from(products);
    }

    @Transactional(readOnly = true)
    public AdminProductResponse get(Long id) { return AdminProductResponse.from(find(id)); }

    @Transactional
    public AdminProductResponse create(Jwt jwt, AdminProductRequest request) {
        String sku = normalizeSku(request.sku());
        validateUnique(request.slug(), sku, null);
        User admin = userService.findCurrentUser(jwt);
        Product product = new Product(request.name().trim(), request.slug(), request.description(),
                request.longDescription(), request.price(), request.oldPrice(),
                request.category().trim(), 0, request.imageUrl(), request.active(),
                request.productType() == null ? ProductType.SINGLE : request.productType());
        product.updateAdmin(product.getName(), product.getSlug(), product.getDescription(),
                product.getLongDescription(), product.getPrice(), product.getOldPrice(),
                request.costPrice(), product.getCategory(),
                product.getImageUrl(), product.getActive(), product.getProductType(), sku);
        productRepository.saveAndFlush(product);
        stockService.recordInitialStock(product, request.stock(), admin);
        return AdminProductResponse.from(product);
    }

    @Transactional
    public AdminProductResponse update(Long id, AdminProductUpdateRequest request) {
        Product product = find(id);
        String sku = normalizeSku(request.sku());
        validateUnique(request.slug(), sku, id);
        product.updateAdmin(request.name().trim(), request.slug(), request.description(),
                request.longDescription(), request.price(), request.oldPrice(), request.costPrice(),
                request.category().trim(), request.imageUrl(), request.active(),
                request.productType(), sku);
        return AdminProductResponse.from(product);
    }

    @Transactional
    public AdminProductResponse setActive(Long id, boolean active) {
        Product product = find(id);
        product.setActive(active);
        return AdminProductResponse.from(product);
    }

    private Product find(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto nÃ£o encontrado: " + id));
    }

    private void validateUnique(String slug, String sku, Long id) {
        boolean slugExists = id == null ? productRepository.existsBySlug(slug)
                : productRepository.existsBySlugAndIdNot(slug, id);
        if (slugExists) throw new ResourceConflictException("Slug jÃ¡ estÃ¡ em uso: " + slug);
        boolean skuExists = id == null ? productRepository.existsBySku(sku)
                : productRepository.existsBySkuAndIdNot(sku, id);
        if (skuExists) throw new ResourceConflictException("SKU jÃ¡ estÃ¡ em uso");
    }

    private String normalizeSku(String value) { return value.trim().toUpperCase(Locale.ROOT); }

    private String cleanOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
