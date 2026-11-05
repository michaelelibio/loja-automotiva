package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.shared.exception.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;

@Service
public class AdminAliExpressTestService {
    public static final String CONFIRMATION="CRIAR PEDIDO REAL DE TESTE NÃO PAGO NO ALIEXPRESS";
    private final OrderRepository orders;
    private final AdminAliExpressTestRunRepository runs;
    private final AliExpressTestReadiness readiness;
    private final AliExpressOrderClient client;
    private final AliExpressTokenService tokens;
    private final Environment environment;
    private final Set<Long> testOrderIds;
    private final boolean realEnabled;
    private final Long fixtureOrderId;
    private final AliExpressTestFixture fixture;
    private final TransactionTemplate tx;

    public AdminAliExpressTestService(OrderRepository orders, AdminAliExpressTestRunRepository runs,
            AliExpressTestReadiness readiness, AliExpressOrderClient client, AliExpressTokenService tokens,
            PlatformTransactionManager manager, Environment environment,
            @Value("${aliexpress.admin-tests.order-ids:}") String ids,
            @Value("${aliexpress.admin-tests.real-enabled:false}") boolean realEnabled,
            @Value("${aliexpress.admin-tests.fixture-order-id:0}") Long fixtureOrderId,
            @Value("${aliexpress.admin-tests.fixture-json:}") String fixtureJson) {
        this.orders=orders; this.runs=runs; this.readiness=readiness; this.client=client; this.tokens=tokens;
        this.environment=environment; this.realEnabled=realEnabled; this.fixtureOrderId=fixtureOrderId;
        testOrderIds=ids.isBlank() ? Set.of() : Set.copyOf(Arrays.stream(ids.split(",")).map(String::trim).map(Long::valueOf).toList());
        AliExpressTestFixture parsed=null;
        try { if (!fixtureJson.isBlank()) parsed=tools.jackson.databind.json.JsonMapper.builder().build().readValue(fixtureJson,AliExpressTestFixture.class); }
        catch (RuntimeException ignored) { /* Never log test recipient data. Malformed configuration stays blocked. */ }
        fixture=parsed;
        tx=new TransactionTemplate(manager); tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public View view(Long id) {
        return tx.execute(s->{ requireOrder(id); return inside(id); });
    }
    public View simulate(Long id, String actor, boolean supplier) {
        return tx.execute(s->{
            requireTest(id); lock(id);
            var row=runs.findById(id).orElseGet(()->new AdminAliExpressTestRun(id));
            if (supplier) {
                if (row.simulatedPaymentAt==null) throw new ResourceConflictException("Simule primeiro o pagamento de teste.");
                if (row.simulatedSupplierAt==null) {
                    row.simulatedSupplierAt=Instant.now(); row.simulatedSupplierBy=actor; row.simulatedSupplierId="SIM-AE-"+id;
                }
            } else if (row.simulatedPaymentAt==null) { row.simulatedPaymentAt=Instant.now(); row.simulatedPaymentBy=actor; }
            runs.saveAndFlush(row); return inside(id);
        });
    }
    public View createReal(Long id, String actor, Confirmation confirmation) {
        requireTest(id);
        // No simulation flag or customer payment is consulted by this operation.
        requireConfirmation(confirmation);
        var before=view(id);
        if (!before.canCreateReal()) throw new ResourceConflictException(String.join(" ",before.realBlockers()));
        String accessToken=tokens.accessToken();
        tx.executeWithoutResult(s->{
            lock(id); var current=inside(id);
            if (!current.canCreateReal()) throw new ResourceConflictException("Criação bloqueada. Confira o registro e reconcilie no AliExpress.");
            requireConfirmation(confirmation);
            var row=runs.findById(id).orElseGet(()->new AdminAliExpressTestRun(id));
            row.realKey=fixture.id(); row.fixtureDigest=fixture.digest(); row.realStatus="VERIFICATION_REQUIRED";
            row.submittedAt=Instant.now(); row.submittedBy=actor;
            // Commit uncertainty BEFORE the only network call. A crash cannot unlock another submission.
            runs.saveAndFlush(row);
        });
        AliExpressOrderClient.Result result;
        try { result=client.create(fixture.payload(),accessToken); }
        catch (RuntimeException ignored) { result=AliExpressOrderClient.Result.unknown(); }
        var outcome=result;
        tx.executeWithoutResult(s->{
            lock(id); var row=runs.findById(id).orElseThrow();
            if (outcome!=null && outcome.orderIds()!=null && !outcome.orderIds().isEmpty()) {
                row.supplierOrderIds=List.copyOf(outcome.orderIds()); row.createdAt=Instant.now(); row.realStatus="CREATED_UNPAID_UNVERIFIED";
            } else if (outcome!=null && outcome.rejected()) row.realStatus="REJECTED_NO_RETRY";
            runs.saveAndFlush(row);
        });
        return view(id);
    }
    private void requireConfirmation(Confirmation c) {
        if (c==null || !CONFIRMATION.equals(c.phrase()) || fixture==null || !fixture.valid()
                || !fixture.id().equals(c.idempotencyKey()) || !fixture.digest().equals(c.fixtureDigest()))
            throw new ResourceConflictException("Confirme explicitamente a criação real não paga e a configuração de teste revisada.");
    }
    private View inside(Long id) {
        var row=runs.findById(id).orElse(null);
        var blockers=new ArrayList<String>();
        boolean test=testOrderIds.contains(id);
        if (!test) blockers.add("Pedido não identificado como teste na configuração administrativa.");
        if (!realEnabled) blockers.add("Criação real de teste desabilitada por padrão.");
        if (environment.acceptsProfiles(Profiles.of("controlled"))) blockers.add("Perfil controlled bloqueia criação real de teste.");
        if (!Objects.equals(id,fixtureOrderId) || fixture==null || !fixture.valid()) blockers.add("Configuração própria e autorizada de teste ausente ou inválida.");
        else {
            blockers.addAll(readiness.blockers(fixture));
            if (runs.existsByRealKeyAndOrderIdNot(fixture.id(),id)) blockers.add("Identificador de teste já utilizado em outro registro. Reenvio bloqueado.");
        }
        if (row!=null && row.realKey!=null) blockers.add("Identificador já utilizado. Reenvio bloqueado; confira e reconcilie manualmente no AliExpress.");
        return new View(id,test,row==null ? null : row.simulatedPaymentAt,row==null ? null : row.simulatedPaymentBy,
                row==null ? null : row.simulatedSupplierAt,row==null ? null : row.simulatedSupplierId,
                row==null ? null : row.simulatedSupplierBy,blockers.isEmpty(),List.copyOf(blockers),
                fixture!=null && fixture.valid() && Objects.equals(id,fixtureOrderId) ? new FixtureView(fixture.id(),fixture.digest(),fixture.authorizationReference(),
                    fixture.productId(),fixture.skuId(),fixture.quantity(),fixture.deliveryService(),fixture.currency(),fixture.productCost(),
                    fixture.shippingCost(),fixture.taxCost(),fixture.totalCost(),fixture.address().get("city")) : null,
                row==null ? null : row.realStatus,row==null ? null : row.realKey,row==null ? null : row.submittedAt,
                row==null ? null : row.createdAt,row==null ? null : row.submittedBy,
                row==null || row.supplierOrderIds==null ? List.of() : row.supplierOrderIds,row==null ? null : row.fixtureDigest);
    }
    private void requireTest(Long id) { if (!testOrderIds.contains(id)) throw new ResourceConflictException("Operação exclusiva para pedidos de teste autorizados."); }
    private void requireOrder(Long id) { if (!orders.existsById(id)) throw new ResourceNotFoundException("Pedido não encontrado."); }
    private void lock(Long id) { orders.findByIdForLifecycleUpdate(id).orElseThrow(()->new ResourceNotFoundException("Pedido não encontrado.")); }
    public record Confirmation(String phrase,String idempotencyKey,String fixtureDigest) {}
    public record FixtureView(String id,String digest,String authorizationReference,String productId,String skuId,int quantity,String deliveryService,
            String currency,java.math.BigDecimal productCost,java.math.BigDecimal shippingCost,java.math.BigDecimal taxCost,java.math.BigDecimal totalCost,String city) {}
    public record View(Long orderId,boolean testOrder,Instant simulatedPaymentAt,String simulatedPaymentBy,Instant simulatedSupplierAt,
            String simulatedSupplierId,String simulatedSupplierBy,boolean canCreateReal,List<String> realBlockers,FixtureView fixture,
            String realStatus,String idempotencyKey,Instant submittedAt,Instant createdAt,String submittedBy,List<String> supplierOrderIds,String submittedFixtureDigest) {}
}
