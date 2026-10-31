package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.order.entity.*;
import com.garage.garageapi.order.fulfillment.*;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.payment.entity.PaymentStatus;
import com.garage.garageapi.payment.repository.PaymentRepository;
import com.garage.garageapi.shared.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;

@Service
public class AliExpressOrderService {
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final OrderFulfillmentRepository fulfillments;
    private final AliExpressOrderPreparation preparation;
    private final AliExpressOrderClient client;
    private final AliExpressTokenService tokens;
    private final TransactionTemplate tx;
    public AliExpressOrderService(OrderRepository orders,PaymentRepository payments,OrderFulfillmentRepository fulfillments,
            AliExpressOrderPreparation preparation,AliExpressOrderClient client,AliExpressTokenService tokens,PlatformTransactionManager manager) {
        this.orders=orders;this.payments=payments;this.fulfillments=fulfillments;this.preparation=preparation;this.client=client;this.tokens=tokens;
        tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public View view(Long id) { return tx.execute(s->viewInside(id)); }
    public View send(Long id) {
        // Obtain authorization before claiming. A token refresh failure cannot have created a purchase.
        // Validate eligibility first so blocked/unpaid requests never touch credentials or the supplier.
        View before=view(id);
        if (before.status()==FulfillmentStatus.CREATED) return before;
        if (!before.canSend()) throw new ResourceConflictException(before.blockReason());
        String accessToken=tokens.accessToken();
        Claim claim=tx.execute(s->{
            Order order=orders.findByIdForLifecycleUpdate(id).orElseThrow(()->new ResourceNotFoundException("Pedido não encontrado."));
            View current=viewInside(id);
            if(current.status()==FulfillmentStatus.CREATED) return null;
            if(!current.canSend()) throw new ResourceConflictException(current.blockReason());
            var fulfillment=fulfillments.findByOrderIdForUpdate(id).orElseGet(()->fulfillments.saveAndFlush(new OrderFulfillment(order,"ALIEXPRESS")));
            fulfillment.prepareAliExpress();
            Map<String,Object> payload=preparation.payload(order,fulfillment.getExternalReference());
            String token=fulfillment.beginAliExpress(Instant.now());
            if(token==null) throw new ResourceConflictException("Verificação necessária. O pedido não pode ser reenviado.");
            fulfillments.flush();
            return new Claim(token,payload);
        });
        if(claim==null) return view(id);
        AliExpressOrderClient.Result result;
        try { result=client.create(claim.payload(),accessToken); }
        catch(RuntimeException ignored) { result=AliExpressOrderClient.Result.unknown(); }
        AliExpressOrderClient.Result outcome=result==null ? AliExpressOrderClient.Result.unknown() : result;
        // If this transaction fails after remote success, the committed uncertainty marker survives.
        tx.executeWithoutResult(s->{
            orders.findByIdForLifecycleUpdate(id).orElseThrow();
            var fulfillment=fulfillments.findByOrderIdForUpdate(id).orElseThrow();
            fulfillment.finishAliExpress(claim.token(),outcome.orderIds(),outcome.rejected(),Instant.now());
            fulfillments.flush();
        });
        return view(id);
    }
    private View viewInside(Long id) {
        Order order=orders.findByIdWithItems(id).orElseThrow(()->new ResourceNotFoundException("Pedido não encontrado."));
        boolean applicable=preparation.hasAliExpress(order);
        var fulfillment=fulfillments.findByOrderId(id).filter(f->"ALIEXPRESS".equals(f.getProvider())).orElse(null);
        FulfillmentStatus status=fulfillment==null ? FulfillmentStatus.PENDING : fulfillment.getStatus();
        boolean paid=payments.findFirstByOrderIdAndStatusOrderByCreatedAtDescIdDesc(id,PaymentStatus.PAID)
                .filter(p->p.getMethod()==com.garage.garageapi.payment.entity.PaymentMethod.MERCADO_PAGO
                        && Boolean.TRUE.equals(p.getProviderLiveMode()) && p.getProviderVerifiedAt()!=null
                        && p.getPaidAt()!=null && p.getProviderPaymentId()!=null && !p.getProviderPaymentId().isBlank()).isPresent();
        String reason=!applicable ? "Este pedido não contém itens AliExpress."
                : !paid || (order.getStatus()!=OrderStatus.PAID && order.getStatus()!=OrderStatus.PROCESSING) ? "Aguardando pagamento confirmado pelo backend."
                : status==FulfillmentStatus.CREATED ? "Pedido vinculado ao fornecedor. Consulte a situacao de cada ID antes do pagamento manual."
                : status==FulfillmentStatus.VERIFICATION_REQUIRED || status==FulfillmentStatus.PROCESSING ? "Verificação necessária. Reenvio bloqueado até reconciliação."
                : preparation.blockedReason(order);
        // A legacy or different supplier fulfillment must never be overwritten automatically.
        var existing=fulfillments.findByOrderId(id).orElse(null);
        if (applicable && existing!=null && !"ALIEXPRESS".equals(existing.getProvider())
                && !("NONE".equals(existing.getProvider()) && existing.getStatus()==FulfillmentStatus.NOT_REQUIRED && existing.getAttemptCount()==0))
            reason="Pedido com fluxo de outro fornecedor. Encaminhamento AliExpress bloqueado.";
        return new View(id,applicable,paid,applicable && reason==null,status,reason,
                fulfillment==null ? null : fulfillment.getSupplierStatus(),
                fulfillment==null ? List.of() : fulfillment.getSupplierOrderIds(),
                fulfillment==null ? null : fulfillment.getLastError(),
                fulfillment==null ? null : fulfillment.getProcessingStartedAt(),
                fulfillment==null ? null : fulfillment.getCreatedExternallyAt(),
                fulfillment==null || fulfillment.getSupplierSync()==null ? null : fulfillment.getSupplierSync().view());
    }
    private record Claim(String token,Map<String,Object> payload) { }
    public record View(Long orderId,boolean applicable,boolean paymentConfirmed,boolean canSend,FulfillmentStatus status,
            String blockReason,String supplierStatus,List<String> supplierOrderIds,String lastError,Instant submittedAt,Instant createdExternallyAt,
            AliExpressSupplierSnapshot.View supplierSync) { }
}
