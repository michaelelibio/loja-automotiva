package com.garage.garageapi.product.repository;

import com.garage.garageapi.product.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    Optional<Product> findBySlug(String slug);

    Optional<Product> findBySlugAndActiveTrue(String slug);

    Optional<Product> findByIdAndActiveTrue(Long id);

    Page<Product> findAllByActiveTrue(Pageable pageable);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    boolean existsBySku(String sku);

    boolean existsBySkuIgnoreCase(String sku);

    boolean existsBySkuAndIdNot(String sku, Long id);

    boolean existsBySupplierIgnoreCaseAndSupplierProductId(String supplier, String supplierProductId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id asc")
    List<Product> findAllByIdInOrderByIdWithLock(@Param("ids") List<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdWithLock(@Param("id") Long id);

    @Query("select coalesce(sum(p.stockQuantity), 0) from Product p where p.fulfillmentType = com.garage.garageapi.product.entity.FulfillmentType.LOCAL_STOCK")
    long sumStockQuantity();

    long countByStockQuantityAndFulfillmentType(int stockQuantity,
                                                com.garage.garageapi.product.entity.FulfillmentType fulfillmentType);
}
