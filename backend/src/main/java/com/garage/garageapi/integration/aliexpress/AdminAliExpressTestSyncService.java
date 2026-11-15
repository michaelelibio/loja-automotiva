package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.shared.exception.ResourceConflictException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;

/** Read-only supplier reconciliation, isolated from financial and production fulfillment state. */
@Service
public class AdminAliExpressTestSyncService {
    private final AdminAliExpressTestService views;
    private final AdminAliExpressTestRunRepository runs;
    private final OrderRepository orders;
    private final AliExpressOrderQueryClient client;
    private final AliExpressTokenService tokens;
    private final boolean enabled;
    private final TransactionTemplate tx;
    public AdminAliExpressTestSyncService(AdminAliExpressTestService views,AdminAliExpressTestRunRepository runs,OrderRepository orders,
            AliExpressOrderQueryClient client,AliExpressTokenService tokens,
            PlatformTransactionManager manager,
            @Value("${aliexpress.admin-tests.real-enabled:false}") boolean enabled) {
        this.views=views;this.runs=runs;this.orders=orders;this.client=client;this.tokens=tokens;
        this.enabled=enabled;
        tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public AdminAliExpressTestService.View sync(Long id) {
        var before=views.view(id);
        if (!Long.valueOf(19).equals(id) || !before.testOrder() || !enabled)
            throw new ResourceConflictException("Consulta real de teste bloqueada: conferir ADMIN, identificação de teste, perfil, contrato e permissão de consulta.");
        if (before.supplierOrderIds().isEmpty()) throw new ResourceConflictException("Sem IDs confirmados. Reconcilie na conta AliExpress; não reenvie a criação.");
        String access=tokens.accessToken();
        String buyer=null;try{buyer=tokens.buyerIdentity().userId();}catch(RuntimeException ignored){}
        final String currentBuyer=buyer;
        var claim=tx.execute(s->{
            orders.findByIdForLifecycleUpdate(id).orElseThrow();var row=runs.findById(id).orElseThrow();
            if(row.preflight!=null && row.preflight.buyerUserId()!=null && currentBuyer!=null && !currentBuyer.equals(row.preflight.buyerUserId()))
                throw new ResourceConflictException("A conta OAuth diverge do comprador registrado nesta tentativa. Reconcilie antes de consultar.");
            if(row.supplierSync!=null && row.supplierSync.token()!=null
                    && row.supplierSync.startedAt().isAfter(Instant.now().minusSeconds(120)))
                throw new ResourceConflictException("Consulta em andamento.");
            String token=UUID.randomUUID().toString();
            row.supplierSync=new AliExpressSupplierSnapshot(token,Instant.now(),row.supplierSync==null?null:row.supplierSync.checkedAt(),
                    row.supplierSync==null?List.of():row.supplierSync.parts());runs.saveAndFlush(row);
            return new Claim(token,List.copyOf(row.supplierOrderIds));
        });
        List<AliExpressSupplierSnapshot.Part> parts=new ArrayList<>();Instant deadline=Instant.now().plusSeconds(30);
        for(String supplierId:claim.ids()) {
            AliExpressSupplierSnapshot.Part part=null;
            try { if(Instant.now().isBefore(deadline)) part=client.queryAdministrative(id,supplierId,access); } catch(RuntimeException ignored) {}
            if(part==null || !supplierId.equals(part.orderId())) part=AliExpressSupplierSnapshot.unknown(supplierId,"LOOKUP_FAILED");
            parts.add(part);
        }
        tx.executeWithoutResult(s->{
            orders.findByIdForLifecycleUpdate(id).orElseThrow();var row=runs.findById(id).orElseThrow();
            if(row.supplierSync!=null && claim.token().equals(row.supplierSync.token())) {
                row.supplierSync=new AliExpressSupplierSnapshot(null,row.supplierSync.startedAt(),Instant.now(),List.copyOf(parts));
                runs.saveAndFlush(row);
            }
        });
        return views.view(id);
    }
    private record Claim(String token,List<String> ids) {}
}
