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

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.core.env.Environment environment;
    private boolean legacy() { return environment.acceptsProfiles(org.springframework.core.env.Profiles.of("local-freight & !controlled & !production & !prod")); }
    private boolean eligible(com.garage.garageapi.product.entity.Product p) {
        return legacy() || ("ALIEXPRESS".equalsIgnoreCase(p.getSupplier())
                && p.getFulfillmentType() == com.garage.garageapi.product.entity.FulfillmentType.DROPSHIPPING);
    }
    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> findAll(Pageable pageable) {
        if (legacy()) return productRepository.findAllByActiveTrue(pageable).map(ProductResponse::from);
        return productRepository.findAll((root, query, cb) -> cb.and(cb.isTrue(root.get("active")),
                cb.equal(cb.upper(root.get("supplier")), "ALIEXPRESS"),
                cb.equal(root.get("fulfillmentType"), com.garage.garageapi.product.entity.FulfillmentType.DROPSHIPPING)), pageable).map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        return productRepository.findByIdAndActiveTrue(id).filter(this::eligible).map(ProductResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Produto nÃ£o encontrado: " + id));
    }

    @Transactional(readOnly = true)
    public ProductResponse findBySlug(String slug) {
        return productRepository.findBySlugAndActiveTrue(slug)
                .filter(this::eligible).map(ProductResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + slug));
    }

}
