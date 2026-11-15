package com.garage.garageapi.integration.aliexpress;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Read-only assistance for an existing order. Never confirms a quote, creates orders or starts payment. */
@Service
public class AliExpressAssistedFreightPreview {
    private final AliExpressTokenService tokens;
    private final AliExpressDsFreightClient freight;
    private final AliExpressProductClient products;
    private final AliExpressProductNormalizer normalizer;
    public AliExpressAssistedFreightPreview(AliExpressTokenService tokens,AliExpressDsFreightClient freight,
            AliExpressProductClient products,AliExpressProductNormalizer normalizer) {
        this.tokens=tokens;this.freight=freight;this.products=products;this.normalizer=normalizer;
    }
    public record Item(String productId,String skuId,int quantity) {}
    // No recipient/CPF/street/phone is needed or transmitted by the supported provider contract.
    public record Context(Long orderId,BigDecimal saleSubtotalBrl,String state,List<Item> items) {
        public Context { items=List.copyOf(items); }
    }
    public record Line(String productId,String skuId,int quantity,String status,
            BigDecimal observedUnitPriceBrl,BigDecimal observedProductsBrl,TaxObservation tax,
            List<AliExpressDsFreightClient.ObservedOption> options) {
        public Line { options=List.copyOf(options); }
    }
    public record TaxObservation(Boolean priceIncludesTax,String receivedTaxAmount,String currency) {}
    public record Costs(BigDecimal observedProductsBrl,BigDecimal confirmedFreightBrl,BigDecimal taxesBrl,
            BigDecimal otherSupplierChargesBrl,BigDecimal supplierTotalBrl,BigDecimal grossMarginBrl,
            BigDecimal netMarginBrl,String status) {
        /** Inputs must be independently confirmed and taxes exclusive of products (avoid double counting). */
        public static Costs calculate(BigDecimal products,BigDecimal freight,BigDecimal taxes,BigDecimal other,
                BigDecimal paymentFees,BigDecimal saleTotal) {
            boolean supplierKnown=java.util.stream.Stream.of(products,freight,taxes,other).allMatch(v->v!=null&&v.signum()>=0);
            BigDecimal total=supplierKnown?products.add(freight).add(taxes).add(other):null;
            BigDecimal gross=total!=null&&saleTotal!=null?saleTotal.subtract(total):null;
            BigDecimal net=gross!=null&&paymentFees!=null&&paymentFees.signum()>=0?gross.subtract(paymentFees):null;
            return new Costs(products,freight,taxes,other,total,gross,net,net==null?"INCOMPLETE":"COMPLETE");
        }
    }
    /** Future adapters must supply every proof. Eligibility never enables payment or supplier submission. */
    public record Evidence(Costs costs,boolean fullAddressConfirmed,boolean scaleConfirmed,boolean methodAndGroupingConfirmed,
            boolean currentSkuPriceAndStockConfirmed,Instant supplierValidUntil) {
        public boolean automaticEligible(Instant now) {
            return costs!=null && "COMPLETE".equals(costs.status()) && costs.netMarginBrl()!=null && costs.netMarginBrl().signum()>=0
                && fullAddressConfirmed && scaleConfirmed && methodAndGroupingConfirmed && currentSkuPriceAndStockConfirmed
                && supplierValidUntil!=null && now.isBefore(supplierValidUntil);
        }
    }
    public record Result(Long orderId,Instant observedAt,Instant reviewBefore,String destinationCoverage,
            String provinceSent,boolean commercialAuthorized,List<Line> lines,Costs costs,List<String> blockers) {
        public Result { lines=List.copyOf(lines);blockers=List.copyOf(blockers); }
    }
    public Result preview(Context context) {
        Instant now=Instant.now();
        List<Line> lines=new ArrayList<>();
        Set<String> blockers=new LinkedHashSet<>(List.of("FULL_ADDRESS_AND_POSTAL_PRICE_NOT_CONFIRMED",
            "SHIPPING_SELECTION_AND_GROUPING_REQUIRE_REVIEW","TAXES_NOT_CONFIRMED","OTHER_SUPPLIER_CHARGES_NOT_CONFIRMED",
            "PAYMENT_FEES_AND_NET_MARGIN_NOT_CONFIRMED","MANUAL_CONFIRMATION_REQUIRED"));
        String province=AliExpressDsFreightClient.BRAZILIAN_PROVINCES.get(context.state());
        String token=null;
        try { token=tokens.existingAccessToken(); } catch(RuntimeException ignored) { /* Safe status below. No refresh. */ }
        Map<String,Optional<AliExpressProductPreview>> productCache=new HashMap<>();
        Map<String,tools.jackson.databind.JsonNode> rawProducts=new HashMap<>();
        boolean pricesComplete=true;BigDecimal observedProducts=BigDecimal.ZERO;
        String stopReason=null;
        long deadline=System.nanoTime()+Duration.ofSeconds(45).toNanos();
        for(int index=0;index<context.items().size();index++) {
            Item item=context.items().get(index);
            String stopped=stopReason!=null?stopReason:token==null || token.isBlank()?"LOCAL_AUTHORIZATION_UNAVAILABLE":
                index>=20 || System.nanoTime()>deadline?"QUERY_BUDGET_EXCEEDED":null;
            if(stopped!=null) {
                lines.add(new Line(item.productId(),item.skuId(),item.quantity(),stopped,null,null,null,List.of()));
                blockers.add(stopped);pricesComplete=false;continue;
            }
            Optional<AliExpressProductPreview> product=productCache.get(item.productId());
            if(product==null) {
                try {
                    var raw=products.get(item.productId(),"BR","BRL","en",token);
                    product=Optional.of(normalizer.normalize(raw,item.productId(),"BR"));rawProducts.put(item.productId(),raw);
                }
                catch(RuntimeException ignored) { product=Optional.empty(); }
                productCache.put(item.productId(),product);
            }
            var variant=product.flatMap(p->p.variants().stream().filter(v->item.skuId().equals(v.skuId())).findFirst()).orElse(null);
            BigDecimal unit=variant!=null && "BRL".equals(variant.currency()) && variant.price()!=null
                    && variant.price().signum()>=0 && variant.availableStock()>=item.quantity()?variant.price():null;
            BigDecimal amount=unit==null?null:unit.multiply(BigDecimal.valueOf(item.quantity()));
            TaxObservation tax=taxObservation(rawProducts.get(item.productId()),item.skuId(),token);
            if(amount==null) {pricesComplete=false;blockers.add("PRODUCT_PRICE_OR_STOCK_NOT_CONFIRMED");}
            else observedProducts=observedProducts.add(amount);
            AliExpressDsFreightClient.Observation observation=freight.queryObserved(token,
                new AliExpressDsFreightClient.Input(item.productId(),item.skuId(),item.quantity(),"BR",province));
            if(observation==null) {
                lines.add(new Line(item.productId(),item.skuId(),item.quantity(),"INVALID_RESPONSE",unit,amount,tax,List.of()));
                blockers.add("FREIGHT_QUERY_FAILED");continue;
            }
            var options=observation.options();
            if(options.isEmpty()) blockers.add("NO_OBSERVED_DELIVERY_OPTIONS");
            if(options.stream().anyMatch(o->o.shippingBrl()==null)) blockers.add("FREIGHT_SCALE_OR_CURRENCY_NOT_CONFIRMED");
            if(options.stream().anyMatch(o->o.code()==null||o.name()==null||o.originCountry()==null||o.estimatedDays()==null))
                blockers.add("LOGISTICS_METADATA_INCOMPLETE");
            lines.add(new Line(item.productId(),item.skuId(),item.quantity(),observation.result().status().name(),unit,amount,tax,options));
            if(Set.of(AliExpressFreightDiagnosticResult.Status.RATE_LIMITED,AliExpressFreightDiagnosticResult.Status.TOKEN_REJECTED,
                    AliExpressFreightDiagnosticResult.Status.PERMISSION_DENIED).contains(observation.result().status())) stopReason=observation.result().status().name();
        }
        // An observed product price and independent line rates are NOT an all-inclusive supplier checkout total.
        Costs costs=Costs.calculate(pricesComplete?observedProducts:null,null,null,null,null,null);
        return new Result(context.orderId(),now,now.plusSeconds(600),province==null?"COUNTRY_ONLY":"COUNTRY_AND_PROVINCE_ONLY",
            province,false,lines,costs,new ArrayList<>(blockers));
    }
    private TaxObservation taxObservation(tools.jackson.databind.JsonNode raw,String sku,String token) {
        if(raw==null) return null;
        var entries=raw.path("ae_item_sku_info_dtos");
        if(!entries.isArray()) entries=entries.path("ae_item_sku_info_d_t_o");
        if(!entries.isArray()) return null;
        for(var entry:entries) if(sku.equals(entry.path("sku_id").asText())) {
            String flag=entry.path("price_include_tax").asText(),amount=entry.path("tax_amount").asText(),currency=entry.path("tax_currency_code").asText();
            return new TaxObservation(Set.of("true","false").contains(flag)?Boolean.valueOf(flag):null,
                amount.matches("[0-9]{1,6}(?:\\.[0-9]{1,4})?")&&!amount.contains(token)?amount:null,
                Set.of("BRL","USD","EUR","GBP","JPY","CNY","CAD","AUD").contains(currency)?currency:null);
        }
        return null;
    }
}
