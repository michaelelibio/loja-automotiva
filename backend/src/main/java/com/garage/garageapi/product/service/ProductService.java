package com.garage.garageapi.product.service;

import com.garage.garageapi.product.dto.ProductResponse;
import com.garage.garageapi.product.repository.ProductRepository;
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

    @Transactional(readOnly = true)
    public Page<ProductResponse> findAll(Pageable pageable) {
        return productRepository.findAllByActiveTrue(pageable).map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        return productRepository.findByIdAndActiveTrue(id).map(ProductResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Produto nÃ£o encontrado: " + id));
    }

    @Transactional(readOnly = true)
    public ProductResponse findBySlug(String slug) {
        return productRepository.findBySlugAndActiveTrue(slug)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + slug));
    }

}
