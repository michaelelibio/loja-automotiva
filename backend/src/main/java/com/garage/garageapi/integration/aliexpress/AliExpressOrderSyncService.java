package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.order.fulfillment.OrderFulfillmentRepository;
import com.garage.garageapi.shared.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;

@Service
public class AliExpressOrderSyncService {
    private final OrderRepository orders;
    private final OrderFulfillmentRepository fulfillments;
    private final AliExpressOrderQueryClient client;
    private final AliExpressOrderQueryPermission permission;
    private final AliExpressTokenService tokens;
    private final AliExpressOrderService views;
    private final boolean enabled,confirmed;
    private final TransactionTemplate tx;
    public AliExpressOrderSyncService(OrderRepository orders,OrderFulfillmentRepository fulfillments,
            AliExpressOrderQueryClient client,AliExpressOrderQueryPermission permission,AliExpressTokenService tokens,
            AliExpressOrderService views,PlatformTransactionManager manager,
            @Value("${aliexpress.orders.sync-enabled:false}") boolean enabled,
            @Value("${aliexpress.orders.sync-contract-confirmed:false}") boolean confirmed) {
        this.orders=orders;this.fulfillments=fulfillments;this.client=client;this.permission=permission;
        this.tokens=tokens;this.views=views;this.enabled=enabled;this.confirmed=confirmed;
        tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public AliExpressOrderService.View sync(Long id) {
        if (!enabled || !confirmed || !permission.verified()) throw new ResourceConflictException("Consulta real AliExpress bloqueada até homologar contrato e permissão.");
        var before=views.view(id);
        if (before.status()!=com.garage.garageapi.order.fulfillment.FulfillmentStatus.CREATED || before.supplierOrderIds().isEmpty())
            throw new ResourceConflictException("Não há IDs AliExpress confirmados. Reconcilie a criação sem reenviar.");
        String access=tokens.accessToken();
        Claim claim=tx.execute(s->{
            orders.findByIdForLifecycleUpdate(id).orElseThrow(()->new ResourceNotFoundException("Pedido não encontrado."));
            var row=fulfillments.findByOrderIdForUpdate(id).orElseThrow();
            String token=row.beginSupplierSync(Instant.now());
            return new Claim(token,row.getSupplierOrderIds());
        });
        List<AliExpressSupplierSnapshot.Part> parts=new ArrayList<>();
        Instant deadline=Instant.now().plusSeconds(30);
        for (String supplierId:claim.ids()) {
            AliExpressSupplierSnapshot.Part part;
            try { part=Instant.now().isBefore(deadline) ? client.query(supplierId,access) : null; }
            catch (RuntimeException ignored) { part=null; }
            if (part==null || !supplierId.equals(part.orderId())) part=AliExpressSupplierSnapshot.unknown(supplierId,"LOOKUP_FAILED");
            parts.add(part);
        }
        tx.executeWithoutResult(s->{
            orders.findByIdForLifecycleUpdate(id).orElseThrow();
            var row=fulfillments.findByOrderIdForUpdate(id).orElseThrow();
            row.finishSupplierSync(claim.token(),claim.ids(),parts,Instant.now());
        });
        return views.view(id);
    }
    private record Claim(String token,List<String> ids) { }
}
