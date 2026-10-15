package com.garage.garageapi.integration.aliexpress;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface AliExpressAuthorizationRepository extends JpaRepository<AliExpressAuthorization, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AliExpressAuthorization a where a.id = :id")
    Optional<AliExpressAuthorization> findLocked(@Param("id") String id);
}
