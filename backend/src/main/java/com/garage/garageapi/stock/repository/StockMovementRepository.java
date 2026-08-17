package com.garage.garageapi.stock.repository;

import com.garage.garageapi.stock.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long>,
        JpaSpecificationExecutor<StockMovement> {
    @Override
    @EntityGraph(attributePaths = {"product", "performedByUser"})
    Page<StockMovement> findAll(Specification<StockMovement> specification, Pageable pageable);
}
