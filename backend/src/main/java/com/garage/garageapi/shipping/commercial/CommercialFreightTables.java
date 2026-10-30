package com.garage.garageapi.shipping.commercial;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface CommercialFreightTables extends JpaRepository<CommercialFreightTable,Long> {
    Optional<CommercialFreightTable> findFirstByPublishedAtIsNotNullOrderByIdDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from CommercialFreightTable t where t.id=:id")
    Optional<CommercialFreightTable> locked(Long id);
}
