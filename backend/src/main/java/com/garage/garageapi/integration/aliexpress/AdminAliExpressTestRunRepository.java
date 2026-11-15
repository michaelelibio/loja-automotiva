package com.garage.garageapi.integration.aliexpress;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAliExpressTestRunRepository extends JpaRepository<AdminAliExpressTestRun, Long> {
    boolean existsByRealKeyAndOrderIdNot(String realKey, Long orderId);
}
