package com.garage.garageapi.integration.aliexpress;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface AliExpressOAuthStateRepository extends JpaRepository<AliExpressOAuthState, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AliExpressOAuthState s where s.id = :id")
    Optional<AliExpressOAuthState> findLocked(@Param("id") String id);
}
