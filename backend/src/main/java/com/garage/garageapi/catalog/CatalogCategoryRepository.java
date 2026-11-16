package com.garage.garageapi.catalog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CatalogCategoryRepository extends JpaRepository<CatalogCategory,Long> {
    List<CatalogCategory> findAllByOrderByPositionAscIdAsc();
    Optional<CatalogCategory> findByPath(String path);
    boolean existsByPath(String path);
}
