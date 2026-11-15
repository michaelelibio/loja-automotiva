package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.order.entity.*;
import com.garage.garageapi.order.fulfillment.OrderFulfillment;
import com.garage.garageapi.order.recipient.AliExpressRecipientData;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.shared.exception.*;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
public class AliExpressRecipientAdminService {
    private final OrderRepository orders;
    private final AliExpressDispatchStore store;
    private final AliExpressRecipientAdminAuditRepository audits;
    private final AdminAliExpressTestRunRepository administrativeRuns;
    public AliExpressRecipientAdminService(OrderRepository orders,AliExpressDispatchStore store,
            AliExpressRecipientAdminAuditRepository audits,AdminAliExpressTestRunRepository administrativeRuns) {
        this.orders=orders;this.store=store;this.audits=audits;this.administrativeRuns=administrativeRuns;
    }
    public record Correction(String cpf,String birthDate,
            String expectedContext) {
        @Override public String toString() {return "AliExpressRecipientCorrection[REDACTED]";}
    }
    public record Reconciliation(String expectedContext,@Min(0) int expectedAttemptCount,
            @NotNull Long recipientRevision,
            @AssertTrue boolean recipientDataConfirmed,@AssertTrue boolean allPreviousAttemptsChecked,
            @AssertTrue boolean noSupplierOrderConfirmed) {
        @Override public String toString() {return "AliExpressRecipientReconciliation[metadata only]";}
    }
    public record View(Long orderId,String maskedCpf,boolean birthDatePresent,boolean phonePresent,boolean complete,
            List<String> pendingFields,boolean canEdit,boolean canReconcile,boolean manualHold,boolean manualRetryReady,
            int attemptCount,Instant lastAttemptAt,String contextDigest,Long recipientRevision,Instant correctedAt) { }

    @Transactional(readOnly=true) public View view(Long id) {
        var order=load(id);return view(order,store.find(id));
    }
    @Transactional public View correct(Long id,Correction request,String actor) {
        var order=orders.findByIdForLifecycleUpdate(id).orElseThrow(()->new ResourceNotFoundException("Pedido não encontrado."));
        requireApplicable(order);requireContext(order,request.expectedContext());requireEditableOrder(order);
        requireNoAdministrativeAttempt(id);
        var row=store.locked(order);
        require(row.recipientEditSafe(Instant.now()),"Pedido vinculado ou tentativa em andamento. Reconcilie o fornecedor antes de alterar os dados.");
        var normalized=AliExpressRecipientData.validateCorrection(request.cpf(),request.birthDate(),order);
        String previous=order.quoteContextDigest();order.snapshotRecipient(normalized);
        row.holdForRecipientCorrection();store.flush(row);
        audits.saveAndFlush(new AliExpressRecipientAdminAudit(id,actor,"RECIPIENT_CORRECTED",previous,order.quoteContextDigest(),row.getAttemptCount()));
        return view(order,row);
    }
    /** Human reconciliation because the current buyer API has no verified complete order-history search. Never creates. */
    @Transactional public View reconcile(Long id,Reconciliation request,String actor) {
        var order=orders.findByIdForLifecycleUpdate(id).orElseThrow(()->new ResourceNotFoundException("Pedido não encontrado."));
        requireApplicable(order);requireContext(order,request.expectedContext());requireEditableOrder(order);requireNoAdministrativeAttempt(id);
        var row=store.locked(order);
        require(request.recipientDataConfirmed() && request.allPreviousAttemptsChecked() && request.noSupplierOrderConfirmed(),"Confirme os dados e a ausência de pedido no histórico completo do fornecedor após todas as tentativas anteriores.");
        var edit=audits.findFirstByOrderIdAndActionOrderByIdDesc(id,"RECIPIENT_CORRECTED").orElseThrow(()->new ResourceConflictException("Salve e confira os dados do destinatário antes da reconciliação."));
        require(Objects.equals(request.recipientRevision(),edit.getId()) && edit.getCurrentContext().equals(order.quoteContextDigest())
                && row.getAttemptCount()==request.expectedAttemptCount(),"Dados ou tentativa alterados. Atualize a página e reconcilie novamente.");
        require(AliExpressRecipientData.status(order).complete(),"Confira os campos obrigatórios do destinatário antes de reconciliar.");
        row.approveManualRetry(order.quoteContextDigest(),Instant.now());store.flush(row);
        audits.save(new AliExpressRecipientAdminAudit(id,actor,"ADMIN_CONFIRMED_ABSENCE",order.quoteContextDigest(),order.quoteContextDigest(),row.getAttemptCount()));
        return view(order,row);
    }
    private Order load(Long id) {var order=orders.findByIdWithItems(id).orElseThrow(()->new ResourceNotFoundException("Pedido não encontrado."));requireApplicable(order);return order;}
    private View view(Order order,OrderFulfillment row) {
        var status=AliExpressRecipientData.status(order);String context=order.quoteContextDigest();
        var edit=audits.findFirstByOrderIdAndActionOrderByIdDesc(order.getId(),"RECIPIENT_CORRECTED").orElse(null);
        boolean editable=Set.of(OrderStatus.PENDING_PAYMENT,OrderStatus.PAID,OrderStatus.PROCESSING).contains(order.getStatus())
                && (row==null || row.recipientEditSafe(Instant.now())) && !hasAdministrativeAttempt(order.getId());
        boolean ready=row!=null && row.manualRetryReady(context);
        boolean canReconcile=editable && row!=null && row.isDispatchManualHold() && !ready && row.getAttemptCount()<3 && edit!=null
                && edit.getCurrentContext().equals(context) && status.complete()
                && (row.getProcessingStartedAt()==null || !Instant.now().isBefore(row.getProcessingStartedAt().plusSeconds(120)));
        String cpf=order.getRecipientCpf();String masked=cpf!=null && cpf.matches("[0-9]{11}")?"***.***.***-"+cpf.substring(9):null;
        return new View(order.getId(),masked,order.getRecipientBirthDate()!=null,order.getRecipientPhone()!=null,status.complete(),status.pendingFields(),
                editable,canReconcile,row!=null && row.isDispatchManualHold(),ready,row==null?0:row.getAttemptCount(),row==null?null:row.getProcessingStartedAt(),
                context,edit==null?null:edit.getId(),edit==null?null:edit.getCreatedAt());
    }
    private boolean hasAdministrativeAttempt(Long id) {var a=administrativeRuns.findById(id).orElse(null);return a!=null && (a.realKey!=null || a.submittedAt!=null || a.supplierOrderIds!=null && !a.supplierOrderIds.isEmpty());}
    private void requireNoAdministrativeAttempt(Long id) {require(!hasAdministrativeAttempt(id),"Tentativa administrativa existente. Use a reconciliação desse fluxo.");}
    private static void requireApplicable(Order o) {require(AliExpressOrderPreparation.aliItems(o).size()>0,"Pedido sem produtos AliExpress.");}
    private static void requireEditableOrder(Order o) {require(Set.of(OrderStatus.PENDING_PAYMENT,OrderStatus.PAID,OrderStatus.PROCESSING).contains(o.getStatus()),"Este pedido não permite correção de destinatário.");}
    private static void requireContext(Order o,String expected) {require(Objects.equals(o.quoteContextDigest(),expected),"O pedido mudou. Atualize a página antes de salvar.");}
    private static void require(boolean condition,String message) {if(!condition)throw new ResourceConflictException(message);}
}
