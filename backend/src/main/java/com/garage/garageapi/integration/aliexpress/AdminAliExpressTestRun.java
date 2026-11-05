package com.garage.garageapi.integration.aliexpress;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Deliberately unrelated to Payment, Order status and production fulfillment. */
@Entity
@Table(name = "admin_aliexpress_test_runs")
public class AdminAliExpressTestRun {
    @Id Long orderId;
    Instant simulatedPaymentAt;
    String simulatedPaymentBy;
    Instant simulatedSupplierAt;
    String simulatedSupplierBy;
    String simulatedSupplierId;
    @Column(unique = true, length = 100) String realKey;
    @Column(length = 64) String fixtureDigest;
    String realStatus;
    Instant submittedAt;
    Instant createdAt;
    String submittedBy;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") List<String> supplierOrderIds;
    protected AdminAliExpressTestRun() {}
    AdminAliExpressTestRun(Long orderId) { this.orderId = orderId; }
}
