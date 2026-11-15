package com.garage.garageapi.integration.aliexpress;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface AliExpressRecipientAdminAuditRepository extends JpaRepository<AliExpressRecipientAdminAudit,Long> {
    Optional<AliExpressRecipientAdminAudit> findFirstByOrderIdAndActionOrderByIdDesc(Long orderId,String action);
}
