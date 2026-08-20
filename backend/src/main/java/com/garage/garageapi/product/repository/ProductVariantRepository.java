package com.garage.garageapi.product.repository;

import com.garage.garageapi.product.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    List<ProductVariant> findAllByProductIdOrderByIdAsc(Long productId);

    Optional<ProductVariant> findBySupplierIgnoreCaseAndSupplierVariantId(
            String supplier, String supplierVariantId);

    boolean existsByProductId(Long productId);
}
