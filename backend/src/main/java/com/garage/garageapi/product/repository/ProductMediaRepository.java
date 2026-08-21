package com.garage.garageapi.product.repository;

import com.garage.garageapi.product.entity.ProductMedia;
import com.garage.garageapi.product.entity.ProductMediaSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductMediaRepository extends JpaRepository<ProductMedia, Long> {
    List<ProductMedia> findAllByProductIdAndSourceOrderByPositionAscIdAsc(
            Long productId, ProductMediaSource source);
}
