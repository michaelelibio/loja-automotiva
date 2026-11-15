package com.garage.garageapi.integration.aliexpress;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface AliExpressDispatchRecordRepository extends JpaRepository<AliExpressDispatchRecord,Long> {
    @Query("""
        select distinct o.id from Order o join o.items i
        where i.supplier='ALIEXPRESS' and o.status in ('PAID','PROCESSING')
          and not exists(select f.id from OrderFulfillment f where f.order=o and f.provider='ALIEXPRESS')
          and (not exists(select a.orderId from AliExpressDispatchRecord a where a.order=o)
               or exists(select a.orderId from AliExpressDispatchRecord a where a.order=o and a.status='PENDING' and a.automaticEligible=true
                   and (a.state is not null) and (a.nextCheckAt is null or a.nextCheckAt <= :now)))
          and exists(select p.id from Payment p where p.order=o and p.method='MERCADO_PAGO' and p.status='PAID'
               and p.providerLiveMode=true and p.providerVerifiedAt is not null and p.paidAt is not null and p.providerPaymentId is not null)
        order by o.id
        """)
    List<Long> recoveryCandidates(@Param("now") Instant now,org.springframework.data.domain.Pageable page);
}
