package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.order.entity.*;
import com.garage.garageapi.order.fulfillment.*;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.payment.entity.*;
import com.garage.garageapi.payment.repository.PaymentRepository;
import com.garage.garageapi.shared.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;

@Service
public class AliExpressOrderService {
    private static final Logger log=LoggerFactory.getLogger(AliExpressOrderService.class);
    private static final int MAX_ATTEMPTS=3;
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final OrderFulfillmentRepository fulfillments;
    private final AdminAliExpressTestRunRepository administrativeRuns;
    private final AliExpressOrderPreparation preparation;
    private final AliExpressOrderClient client;
    private final AliExpressTokenService tokens;
    private final AliExpressDispatchValidation validation;
    private final AliExpressDispatchStore store;
    private final TransactionTemplate tx;
    private final AliExpressAutomationPolicy automation;
    public AliExpressOrderService(OrderRepository orders,PaymentRepository payments,OrderFulfillmentRepository fulfillments,
            AliExpressOrderPreparation preparation,AliExpressOrderClient client,AliExpressTokenService tokens,
            PlatformTransactionManager manager,AliExpressDispatchValidation validation,AdminAliExpressTestRunRepository administrativeRuns,AliExpressDispatchStore store,
            AliExpressAutomationPolicy automation) {
        this.orders=orders;this.payments=payments;this.fulfillments=fulfillments;this.preparation=preparation;
        this.client=client;this.tokens=tokens;this.validation=validation;this.administrativeRuns=administrativeRuns;this.store=store;this.automation=automation;
        tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public View view(Long id) { return tx.execute(s->viewInside(id)); }

    /** Repeated webhooks and recovery scans never retry a submitted or definitively rejected purchase. */
    public void sendAutomatically(Long id) {
        if(!automation.enabled())return;
        if(!view(id).paymentConfirmed())return;
        boolean due=tx.execute(s->{
            var order=orders.findByIdForLifecycleUpdate(id).orElseThrow();
            if(automation.blockedReason(order)!=null)return false;
            if(order.isPaymentReviewRequired())return false;
            if(!preparation.hasAliExpress(order))return false;
            var row=store.locked(order);return row.automaticDispatchDue(Instant.now());
        });
        if(!due)return;
        try { send(id,true); }
        catch(ResourceConflictException ex) {
            recordBlock(id,ex.getMessage(),false);
        } catch(AliExpressIntegrationException ex) {
            boolean temporary=Set.of(AliExpressIntegrationException.Reason.TIMEOUT,AliExpressIntegrationException.Reason.COMMUNICATION,
                    AliExpressIntegrationException.Reason.RATE_LIMIT).contains(ex.getReason());
            recordBlock(id,ex.getMessage(),temporary);
        } catch(RuntimeException ignored) {
            recordBlock(id,"Falha de comunicação na validação. Confira a situação antes de reenviar.",true);
            log.warn("AliExpress dispatch failed; orderId={}; stage=validation_or_persistence",id);
        }
    }
    private void recordBlock(Long id,String message,boolean temporary) {
        tx.executeWithoutResult(s->{
            var order=orders.findByIdForLifecycleUpdate(id).orElseThrow();
            if(order.isPaymentReviewRequired())return;
            if(!preparation.hasAliExpress(order))return;
            var row=store.locked(order);
            var current=viewInside(id);
            if(!current.paymentConfirmed())return;
            if(message!=null && message.startsWith("Pagamento atual não confirmado")) row.recordPaymentDispatchBlock(message);
            else row.recordDispatchBlock(current.blockReason()==null?message:current.blockReason(),temporary);
            store.flush(row);
        });
    }
    public View send(Long id) {
        try { return send(id,false); }
        catch(ResourceConflictException ex) { recordBlock(id,ex.getMessage(),false);throw ex; }
    }
    private View send(Long id,boolean automatic) {
        View before=view(id);
        if(before.status()==FulfillmentStatus.CREATED)return before;
        if(!before.canSend())throw new ResourceConflictException(before.blockReason());
        var snapshot=tx.execute(s->{
            var order=orders.findByIdWithItems(id).orElseThrow();
            var payment=confirmedPayment(id).orElseThrow();
            return new Preflight(order.quoteContextDigest(),
                    payment.getId(),payment.getProviderPaymentId(),order.getTotal());
        });
        var access=tokens.dispatchAccess();
        String accessToken=access.token();
        var technical=tx.execute(s->preparation.technicalSnapshot(orders.findByIdWithItems(id).orElseThrow(),access.buyerHash(),validation.appHash()));
        Instant checkedAt=Instant.now();
        validation.verifyPayment(id,snapshot.paymentId(),snapshot.providerPaymentId(),snapshot.total());
        var costs=validation.verifyProducts(technical,accessToken,access.buyerHash());
        Claim claim=tx.execute(s->{
            Order order=orders.findByIdForLifecycleUpdate(id).orElseThrow(()->new ResourceNotFoundException("Pedido não encontrado."));
            View current=viewInside(id);
            if(current.status()==FulfillmentStatus.CREATED)return null;
            if(!current.canSend())throw new ResourceConflictException(current.blockReason());
            var row=store.locked(order);
            if(automatic && automation.blockedReason(order)!=null)return null;
            if(automatic && !row.automaticDispatchDue(Instant.now()))return null;
            if(!Objects.equals(snapshot.contextDigest(),order.quoteContextDigest()) || !Objects.equals(technical.contextDigest(),order.quoteContextDigest())
                    || !technical.lines().equals(preparation.technicalSnapshot(order,access.buyerHash(),validation.appHash()).lines())
                    || Instant.now().isAfter(checkedAt.plusSeconds(30)))throw new ResourceConflictException("Pedido ou prazo de validação técnica alterado. Reconfira antes de enviar.");
            row.prepareAliExpress();
            Map<String,Object> payload=preparation.payload(order,row.getExternalReference(),row.getDispatchQuote());
            String payloadHash=AliExpressOAuthStateService.hash(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(payload));
            String token=row.beginAliExpress(Instant.now());
            if(token==null)throw new ResourceConflictException("Verificação necessária. Reenvio bloqueado.");
            row.recordSupplierCosts(costs);row.recordDispatchPayloadHash(payloadHash);store.flush(row);
            return new Claim(token,payload,row.getAttemptCount());
        });
        if(claim==null)return view(id);
        log.info("AliExpress dispatch submitted; orderId={}; attempt={}",id,claim.attempt());
        AliExpressOrderClient.Result result;
        try { result=client.create(claim.payload(),accessToken); }
        catch(RuntimeException ignored) { result=AliExpressOrderClient.Result.unknown(); }
        var outcome=result==null?AliExpressOrderClient.Result.unknown():result;
        tx.executeWithoutResult(s->{
            var order=orders.findByIdForLifecycleUpdate(id).orElseThrow();
            var row=store.locked(order);
            row.finishAliExpress(claim.token(),outcome.confirmed()?outcome.orderIds():List.of(),outcome.rejected(),Instant.now());
            if(!outcome.confirmed())row.recordUnconfirmedAliExpressResult(outcome.orderIds(),outcome.providerError());
            store.flush(row);
        });
        var after=view(id);
        log.info("AliExpress dispatch completed; orderId={}; attempt={}; status={}",id,claim.attempt(),after.status());
        return after;
    }
    private Optional<Payment> confirmedPayment(Long id) {
        return payments.findFirstByOrderIdAndStatusOrderByCreatedAtDescIdDesc(id,PaymentStatus.PAID)
                .filter(p->p.getMethod()==PaymentMethod.MERCADO_PAGO && Boolean.TRUE.equals(p.getProviderLiveMode())
                        && p.getProviderVerifiedAt()!=null && p.getPaidAt()!=null && p.getProviderPaymentId()!=null && !p.getProviderPaymentId().isBlank());
    }
    private View viewInside(Long id) {
        Order order=orders.findByIdWithItems(id).orElseThrow(()->new ResourceNotFoundException("Pedido não encontrado."));
        boolean applicable=preparation.hasAliExpress(order);
        var existing=fulfillments.findByOrderId(id).orElse(null);
        var row=store.find(id);
        var status=row==null?FulfillmentStatus.PENDING:row.getStatus();
        boolean paid=confirmedPayment(id).isPresent();
        var quote=row==null?null:row.getDispatchQuote();
        String reason=!applicable?"Este pedido não contém itens AliExpress."
                :order.isPaymentReviewRequired()?"Pagamento exige revisão financeira. Confira estorno, contestação ou pagamentos divergentes antes de encaminhar."
                :!paid || (order.getStatus()!=OrderStatus.PAID && order.getStatus()!=OrderStatus.PROCESSING)?"Aguardando pagamento confirmado pelo backend."
                :status==FulfillmentStatus.CREATED?"Pedido vinculado ao fornecedor. Confira o estado atual antes do pagamento manual."
                :status==FulfillmentStatus.VERIFICATION_REQUIRED || status==FulfillmentStatus.PROCESSING?"Verificação necessária. Reenvio bloqueado até reconciliação."
                :row!=null && row.getAttemptCount()>=MAX_ATTEMPTS?"Limite de três tentativas de criação atingido. Reconcilie no fornecedor."
                :row!=null && row.isDispatchManualHold() && !row.manualRetryReady(order.quoteContextDigest())?"Dados corrigidos: reconcilie a tentativa anterior no fornecedor antes de um novo envio manual."
                :preparation.blockedReason(order,quote);
        if(reason==null)reason=validation.blockedReason(order,quote);
        if(row!=null && status!=FulfillmentStatus.CREATED && (row.getSupplierOrderId()!=null || !row.getSupplierOrderIds().isEmpty()))
            reason="Referência de fornecedor existente. Reconcilie antes de qualquer criação.";
        var admin=administrativeRuns.findById(id).orElse(null);
        if(admin!=null && (admin.realKey!=null || admin.submittedAt!=null || admin.supplierOrderIds!=null && !admin.supplierOrderIds.isEmpty()))
            reason="Tentativa administrativa AliExpress existente. Use a reconciliação desse pedido; não crie novamente.";
        var costs=row==null || row.getSupplierCosts()==null?AliExpressSupplierCosts.unknown():row.getSupplierCosts();
        return new View(id,applicable,paid,applicable && reason==null,status,reason,row==null?null:row.getSupplierStatus(),
                row==null?List.of():row.getSupplierOrderIds(),row==null?null:row.getLastError(),row==null?null:row.getProcessingStartedAt(),
                row==null?null:row.getCreatedExternallyAt(),row==null || row.getSupplierSync()==null?null:row.getSupplierSync().view(),
                row==null?0:row.getAttemptCount(),MAX_ATTEMPTS,row==null?0:row.getDispatchCheckCount(),row==null?null:row.getDispatchNextCheckAt(),
                order.quoteContextDigest(),quote,automation.enabled(),costs,costs.warnings(order.getTotal()),
                applicable ? automation.blockedReason(order)!=null ? automation.blockedReason(order) : reason : null,
                automation.ordersCreatedFrom());
    }
    private record Claim(String token,Map<String,Object> payload,int attempt) { }
    private record Preflight(String contextDigest,Long paymentId,String providerPaymentId,java.math.BigDecimal total) { }
    public record View(Long orderId,boolean applicable,boolean paymentConfirmed,boolean canSend,FulfillmentStatus status,
            String blockReason,String supplierStatus,List<String> supplierOrderIds,String lastError,Instant submittedAt,Instant createdExternallyAt,
            AliExpressSupplierSnapshot.View supplierSync,int attemptCount,int maxAttempts,int validationAttemptCount,Instant nextValidationAt,
            String contextDigest,AliExpressDispatchQuote dispatchQuote,boolean automaticEnabled,
            AliExpressSupplierCosts supplierCosts,List<String> financialWarnings,
            String automaticBlockReason,Instant automaticOrdersCreatedFrom) { }
}
