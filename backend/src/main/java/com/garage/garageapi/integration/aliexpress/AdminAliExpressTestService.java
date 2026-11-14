package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.product.repository.ProductVariantRepository;
import com.garage.garageapi.shared.exception.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class AdminAliExpressTestService {
    public static final String CONFIRMATION="CRIAR PEDIDO REAL DE TESTE NÃO PAGO NO ALIEXPRESS";
    private final OrderRepository orders;
    private final ProductVariantRepository variants;
    private final AdminAliExpressTestRunRepository runs;
    private final AliExpressTestReadiness readiness;
    private final AliExpressOrderClient client;
    private final AliExpressTokenService tokens;
    private final Set<Long> testOrderIds;
    private final boolean realEnabled;
    private final TransactionTemplate tx;
    public AdminAliExpressTestService(OrderRepository orders,ProductVariantRepository variants,AdminAliExpressTestRunRepository runs,
            AliExpressTestReadiness readiness,AliExpressOrderClient client,AliExpressTokenService tokens,PlatformTransactionManager manager,Environment environment,
            @Value("${aliexpress.admin-tests.order-ids:}") String ids,@Value("${aliexpress.admin-tests.real-enabled:false}") boolean realEnabled) {
        this.orders=orders;this.variants=variants;this.runs=runs;this.readiness=readiness;this.client=client;this.tokens=tokens;this.realEnabled=realEnabled;
        testOrderIds=ids.isBlank()?Set.of():Set.copyOf(Arrays.stream(ids.split(",")).map(String::trim).map(Long::valueOf).toList());
        tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public View view(Long id){return tx.execute(s->{requireOrder(id);return inside(id);});}
    public AliExpressTokenService.BuyerIdentity buyerIdentity(Long id){requireRealScope(id);requireOrder(id);try{return tokens.buyerIdentity();}catch(RuntimeException ignored){return new AliExpressTokenService.BuyerIdentity(null,null);}}
    public PayloadReview reviewPayload(Long id){return tx.execute(s->{requireRealScope(id);var base=snapshot(id);return review(id,base,null);});}
    /** Product reads only; the create endpoint is never invoked while preparing confirmation. */
    public PayloadReview preflight(Long id,String actor) {
        requireRealScope(id);var before=view(id);if(!before.canCreateReal())throw new ResourceConflictException(String.join(" ",before.realBlockers()));
        var base=tx.execute(s->snapshot(id));String access=tokens.accessToken();var check=readiness.check(base,access);
        return tx.execute(s->{
            lock(id);var current=snapshot(id);if(!inside(id).canCreateReal() || !current.payloadDigest().equals(base.payloadDigest()))throw new ResourceConflictException("Pedido alterado ou tentativa já registrada. Atualize os dados.");
            var reviewed=check.fixture();if(reviewed==null || !reviewed.valid() || !reviewed.payloadDigest().equals(current.payloadDigest()))throw new ResourceConflictException("Revisão diverge do pedido autorizado.");
            var row=runs.findById(id).orElseGet(()->new AdminAliExpressTestRun(id));
            row.preflight=new AliExpressAdminPreflight(UUID.randomUUID().toString(),actor,reviewed.digest(),current.payloadDigest(),AliExpressOAuthStateService.hash(access),
                    check.buyerUserId(),check.buyerAccount(),check.buyerState(),check.checkedAt(),check.validUntil(),reviewed,check.warnings(),check.productCostSource(),inGarageTotal(id));
            runs.saveAndFlush(row);return review(id,reviewed,row.preflight);
        });
    }
    private PayloadReview review(Long id,AliExpressTestFixture fixture,AliExpressAdminPreflight p){return new PayloadReview(id,fixture.digest(),fixture.authorizationReference(),fixture.payload(),p==null?null:p.id(),
            p==null?"Conta vinculada ao OAuth existente — consulte a revisão antes de confirmar":p.buyerAccount(),p==null?"UNKNOWN":p.buyerState(),p==null?null:p.checkedAt(),p==null?null:p.validUntil(),fixtureView(fixture),
            p==null?List.of("Frete, impostos e total podem ser desconhecidos; a revisão não efetua compra."):p.warnings(),p==null?"IMPORTED_VARIANT":p.productCostSource(),p==null?inGarageTotal(id):p.inGarageProductTotal());}
    public record PayloadReview(Long orderId,String digest,String authorizationReference,Map<String,Object> payload,String preflightId,String buyerAccount,String buyerState,
            Instant checkedAt,Instant validUntil,FixtureView costs,List<String> warnings,String productCostSource,BigDecimal inGarageProductTotal){@Override public String toString(){return "PayloadReview[REDACTED]";}}
    public View simulate(Long id,String actor,boolean supplier){return tx.execute(s->{requireTest(id);lock(id);var row=runs.findById(id).orElseGet(()->new AdminAliExpressTestRun(id));
        if(supplier){if(row.simulatedPaymentAt==null)throw new ResourceConflictException("Simule primeiro o pagamento de teste.");if(row.simulatedSupplierAt==null){row.simulatedSupplierAt=Instant.now();row.simulatedSupplierBy=actor;row.simulatedSupplierId="SIM-AE-"+id;}}
        else if(row.simulatedPaymentAt==null){row.simulatedPaymentAt=Instant.now();row.simulatedPaymentBy=actor;}runs.saveAndFlush(row);return inside(id);});}
    public View createReal(Long id,String actor,Confirmation c) {
        requireRealScope(id);requireConfirmation(c);var before=view(id);if(!before.canCreateReal())throw new ResourceConflictException(String.join(" ",before.realBlockers()));
        tx.executeWithoutResult(s->requirePreflight(runs.findById(id).orElseThrow(()->new ResourceConflictException("Revisão obrigatória.")),actor,c,snapshot(id),null));
        String access=tokens.accessToken();
        var payload=tx.execute(s->{
            lock(id);var current=snapshot(id);if(!inside(id).canCreateReal())throw new ResourceConflictException("Tentativa já registrada. Reenvio bloqueado até reconciliação.");
            var row=runs.findById(id).orElseThrow();requirePreflight(row,actor,c,current,access);
            row.realKey=row.preflight.fixture().id();row.fixtureDigest=row.preflight.fixtureDigest();row.realStatus="VERIFICATION_REQUIRED";row.submittedAt=Instant.now();row.submittedBy=actor;
            // An independent COMMITTED transaction precedes the sole supplier create request.
            runs.saveAndFlush(row);return row.preflight.fixture().payload();
        });
        AliExpressOrderClient.Result result;try{result=client.createAdministrative(id,payload,access);}catch(RuntimeException ignored){result=AliExpressOrderClient.Result.unknown();}
        var outcome=result;
        tx.executeWithoutResult(s->{lock(id);var row=runs.findById(id).orElseThrow();
            if(outcome!=null){row.providerError=outcome.providerError();if(outcome.orderIds()!=null && !outcome.orderIds().isEmpty()){row.supplierOrderIds=List.copyOf(outcome.orderIds());if(outcome.confirmed()){row.createdAt=Instant.now();row.realStatus="CREATED_UNPAID_UNVERIFIED";}}
                else if(outcome.rejected())row.realStatus="REJECTED_NO_RETRY";}
            runs.saveAndFlush(row);
        });return view(id);
    }
    private void requireConfirmation(Confirmation c){if(c==null || !CONFIRMATION.equals(c.phrase()) || !c.acceptKnownAndUnknownCosts() || c.preflightId()==null || c.preflightId().isBlank())throw new ResourceConflictException("Confirme explicitamente a compra real não paga e o aceite dos custos conhecidos, desconhecidos e possivelmente superiores ao valor da inGarage.");}
    private void requirePreflight(AdminAliExpressTestRun row,String actor,Confirmation c,AliExpressTestFixture current,String token){var p=row.preflight;
        if(p==null || p.fixture()==null || !p.fixture().valid() || !Objects.equals(p.actor(),actor) || !Objects.equals(p.id(),c.preflightId())
                || !Objects.equals(p.fixtureDigest(),c.fixtureDigest()) || !Objects.equals(p.fixture().id(),c.idempotencyKey()) || p.validUntil()==null || !p.validUntil().isAfter(Instant.now())
                || !Objects.equals(p.orderSnapshotDigest(),current.payloadDigest()) || (token!=null && !Objects.equals(p.tokenSha256(),AliExpressOAuthStateService.hash(token))))
            throw new ResourceConflictException("Revisão ausente, expirada ou dados/OAuth alterados. Reabra a confirmação antes de criar.");}
    private View inside(Long id){var row=runs.findById(id).orElse(null);var blockers=new ArrayList<String>();boolean test=testOrderIds.contains(id);AliExpressTestFixture fixture=null;
        if(!test)blockers.add("Pedido não identificado como teste administrativo autorizado.");if(!Long.valueOf(19).equals(id))blockers.add("Criação real exclusiva do pedido próprio #19.");if(!realEnabled)blockers.add("Criação administrativa desabilitada na configuração do serviço.");
        if(Long.valueOf(19).equals(id)){try{fixture=snapshot(id);}catch(ResourceConflictException ex){blockers.add(ex.getMessage());}}
        if(row!=null && row.realKey!=null)blockers.add("Tentativa já registrada. Não reenvie: consulte os IDs ou reconcilie em Meus pedidos na conta do OAuth.");
        return new View(id,test,row==null?null:row.simulatedPaymentAt,row==null?null:row.simulatedPaymentBy,row==null?null:row.simulatedSupplierAt,row==null?null:row.simulatedSupplierId,row==null?null:row.simulatedSupplierBy,
            blockers.isEmpty(),List.copyOf(blockers),fixture==null?null:fixtureView(fixture),row==null?null:row.realStatus,row==null?null:row.realKey,row==null?null:row.submittedAt,row==null?null:row.createdAt,row==null?null:row.submittedBy,
            row==null || row.supplierOrderIds==null?List.of():row.supplierOrderIds,row==null?null:row.fixtureDigest,row==null || row.supplierSync==null?null:row.supplierSync.view(),row==null?null:row.providerError);}
    private AliExpressTestFixture snapshot(Long id){
        if(!Long.valueOf(19).equals(id))throw new ResourceConflictException("Operação real exclusiva do pedido #19.");
        var order=orders.findByIdWithItems(id).orElseThrow(()->new ResourceNotFoundException("Pedido não encontrado."));
        if(order.getItems().size()!=1 || !com.garage.garageapi.order.recipient.AliExpressRecipientData.status(order).complete())throw new ResourceConflictException("Item ou dados obrigatórios do destinatário incompletos no pedido #19.");
        var item=order.getItems().get(0);var variant=item.getProductVariantId()==null?null:variants.findById(item.getProductVariantId()).orElse(null);
        if(!"ALIEXPRESS".equalsIgnoreCase(item.getSupplier()) || item.getFulfillmentType()!=com.garage.garageapi.product.entity.FulfillmentType.DROPSHIPPING || variant==null || !"ALIEXPRESS".equalsIgnoreCase(variant.getSupplier())
                || !Objects.equals(item.getProductId(),variant.getProduct().getId()) || !Objects.equals(item.getSupplierProductId(),variant.getSupplierProductId()) || !Objects.equals(item.getSupplierVariantId(),variant.getSupplierVariantId()))throw new ResourceConflictException("Identificadores originais de produto/SKU ausentes ou divergentes no pedido #19.");
        var leg=order.getShippingLegs().stream().filter(l->"ALIEXPRESS".equals(l.provider()) && l.supplierVariantIds()!=null && l.supplierVariantIds().contains(item.getSupplierVariantId())).findFirst().orElse(null);
        BigDecimal cost="BRL".equals(variant.getSupplierCostCurrency())?variant.getSupplierCost():null;
        Map<String,String> address=new LinkedHashMap<>();AliExpressOrderPreparation.logisticsAddress(order).forEach((k,v)->address.put(k,(String)v));
        var fixture=new AliExpressTestFixture("ingarage-test-19-v1","Pedido próprio #19 autorizado pelo proprietário",item.getSupplierProductId(),item.getSupplierVariantId(),variant.getRawVariantKey(),item.getQuantity(),leg==null?null:leg.code(),"BRL",
                cost==null?null:cost.multiply(BigDecimal.valueOf(item.getQuantity())),leg!=null && "BRL".equals(leg.currency())?leg.amount():null,null,null,address,order.getRecipientBirthDate());
        if(!fixture.valid())throw new ResourceConflictException("Dados obrigatórios ou quantidade inválidos para criar o pedido #19.");return fixture;
    }
    private BigDecimal inGarageTotal(Long id){return orders.findByIdWithItems(id).orElseThrow().getItems().stream().map(com.garage.garageapi.order.entity.OrderItem::getSubtotal).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private FixtureView fixtureView(AliExpressTestFixture f){return new FixtureView(f.id(),f.digest(),f.authorizationReference(),f.productId(),f.skuId(),f.quantity(),f.deliveryService(),f.currency(),f.productCost(),f.shippingCost(),f.taxCost(),f.totalCost(),f.address().get("city"));}
    private void requireRealScope(Long id){requireTest(id);if(!Long.valueOf(19).equals(id))throw new ResourceConflictException("Criação real exclusiva do pedido próprio #19.");}
    private void requireTest(Long id){if(!testOrderIds.contains(id))throw new ResourceConflictException("Operação exclusiva de pedidos administrativos autorizados.");}
    private void requireOrder(Long id){if(!orders.existsById(id))throw new ResourceNotFoundException("Pedido não encontrado.");}
    private void lock(Long id){orders.findByIdForLifecycleUpdate(id).orElseThrow(()->new ResourceNotFoundException("Pedido não encontrado."));}
    public record Confirmation(String phrase,String idempotencyKey,String fixtureDigest,String preflightId,boolean acceptKnownAndUnknownCosts) {}
    public record FixtureView(String id,String digest,String authorizationReference,String productId,String skuId,int quantity,String deliveryService,String currency,BigDecimal productCost,BigDecimal shippingCost,BigDecimal taxCost,BigDecimal totalCost,String city) {}
    public record View(Long orderId,boolean testOrder,Instant simulatedPaymentAt,String simulatedPaymentBy,Instant simulatedSupplierAt,String simulatedSupplierId,String simulatedSupplierBy,boolean canCreateReal,List<String> realBlockers,FixtureView fixture,
            String realStatus,String idempotencyKey,Instant submittedAt,Instant createdAt,String submittedBy,List<String> supplierOrderIds,String submittedFixtureDigest,AliExpressSupplierSnapshot.View supplierSync,AliExpressOrderClient.ProviderError providerError) {}
}
