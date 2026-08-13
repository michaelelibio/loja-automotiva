package com.garage.garageapi.product.service;

import com.garage.garageapi.product.dto.ProductRequest;
import com.garage.garageapi.product.dto.ProductResponse;
import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.repository.ProductRepository;
import com.garage.garageapi.shared.exception.ResourceConflictException;
import com.garage.garageapi.shared.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        validateUniqueSlug(request.slug(), null);
        Product product = new Product(
                request.name().trim(), request.slug(), request.description(), request.longDescription(),
                request.price(), request.oldPrice(), request.category().trim(), request.stockQuantity(),
                request.imageUrl(), request.active() == null ? true : request.active(), request.productType()
        );
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> findAll(Pageable pageable) {
        return productRepository.findAll(pageable).map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        return ProductResponse.from(findEntity(id));
    }

    @Transactional(readOnly = true)
    public ProductResponse findBySlug(String slug) {
        return productRepository.findBySlug(slug)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + slug));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = findEntity(id);
        validateUniqueSlug(request.slug(), id);
        product.update(
                request.name().trim(), request.slug(), request.description(), request.longDescription(),
                request.price(), request.oldPrice(), request.category().trim(), request.stockQuantity(),
                request.imageUrl(), request.active() == null ? product.getActive() : request.active(),
                request.productType()
        );
        return ProductResponse.from(product);
    }

    @Transactional
    public void delete(Long id) {
        productRepository.delete(findEntity(id));
    }

    private Product findEntity(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + id));
    }

    private void validateUniqueSlug(String slug, Long productId) {
        boolean slugInUse = productId == null
                ? productRepository.existsBySlug(slug)
                : productRepository.existsBySlugAndIdNot(slug, productId);

        if (slugInUse) {
            throw new ResourceConflictException("Slug já está em uso: " + slug);
        }
    }
}
