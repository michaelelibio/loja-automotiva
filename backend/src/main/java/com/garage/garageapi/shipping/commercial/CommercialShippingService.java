package com.garage.garageapi.shipping.commercial;

import com.garage.garageapi.product.repository.*;
import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.product.entity.FulfillmentType;
import com.garage.garageapi.shipping.provider.ShippingProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Validator;
import java.math.*;
import java.time.*;
import java.util.*;
import static com.garage.garageapi.shipping.commercial.CommercialFreightPolicy.*;

@Service
public class CommercialShippingService {
    private final CommercialFreightTables tables; private final ProductRepository products;
    private final ProductVariantRepository variants; private final PostalStateResolver postal; private final Validator validator;
    private final boolean enabled;
    public CommercialShippingService(CommercialFreightTables tables,ProductRepository products,ProductVariantRepository variants,
            PostalStateResolver postal,Validator validator,@Value("${app.shipping.commercial.enabled:false}") boolean enabled) {
        this.tables=tables;this.products=products;this.variants=variants;this.postal=postal;this.validator=validator;this.enabled=enabled;
    }
    public record View(Long version,Document document,Instant createdAt,Instant publishedAt,boolean enabled){}
    public record Decision(ShippingProvider.Option option,CommercialShippingSnapshot snapshot){}
    public boolean available(){return enabled;}
    @Transactional(readOnly=true) public List<View> list(){return tables.findAll(org.springframework.data.domain.Sort.by("id").descending()).stream().map(this::view).toList();}
    @Transactional public View draft(Document document){
        require(document.reviewRows().isEmpty(),"Use a revisão em lote para linhas planejadas.");
        require(validator.validate(document).isEmpty(),"Documento incompleto ou valores inválidos.");
        require(document.profiles().stream().map(Profile::variantId).distinct().count()==document.profiles().size(),"SKU duplicado na tabela.");
        require(document.rates().stream().map(Rate::key).distinct().count()==document.rates().size(),"Chave de tarifa duplicada.");
        require(document.rates().stream().map(r->List.of(r.state(),r.category(),r.size(),r.supplierStoreId(),r.originCountry())).distinct().count()==document.rates().size(),"Duas tarifas atendem ao mesmo destino/categoria/porte/origem.");
        for(var rate:document.rates()) require(com.garage.garageapi.integration.aliexpress.AliExpressDsFreightClient.BRAZILIAN_PROVINCES.containsKey(rate.state()),"UF inválida.");
        return view(tables.saveAndFlush(new CommercialFreightTable(document)));
    }
    /** Imports are new immutable versions. The submitted rates list is deliberately ignored. */
    @Transactional public View bulkDraft(Document document){
        require(document!=null && !document.reviewRows().isEmpty() && document.reviewRows().size()<=2000,"Importe entre 1 e 2000 linhas.");
        require(document.profiles()==null || document.profiles().size()<=500,"Limite de SKUs excedido.");
        Set<Object> selectors=new HashSet<>();Set<String> keys=new HashSet<>();List<Rate> approved=new ArrayList<>();
        for(var row:document.reviewRows()) {
            require(row!=null && row.rate()!=null,"Linha inválida.");var r=row.rate();
            require(r.state()!=null && com.garage.garageapi.integration.aliexpress.AliExpressDsFreightClient.BRAZILIAN_PROVINCES.containsKey(r.state())
                && r.size()!=null && r.category()!=null && !r.category().isBlank() && r.supplierStoreId()!=null && !r.supplierStoreId().isBlank()
                && r.originCountry()!=null && Arrays.asList(Locale.getISOCountries()).contains(r.originCountry())
                && r.key()!=null && !r.key().isBlank(),"Identifique UF, porte, categoria, fornecedor, origem e chave de cada linha.");
            require(selectors.add(List.of(r.state(),r.category(),r.size(),r.supplierStoreId(),r.originCountry())) && keys.add(r.key()),"Tarifa duplicada na importação.");
            if(row.approved()) {
                require(validator.validate(r).isEmpty(),"Tarifa aprovada incompleta ou com valores inválidos: "+r.key());
                r.evidence().requireValid(Instant.now());
                require(r.packagingVerified() && r.evidence().source()!=Source.PAYMENT_PROVIDER_PLAN,"Aprovação exige embalagem e fonte AliExpress comprovadas.");
                require(document.profiles()!=null && document.profiles().stream().anyMatch(p->p!=null && p.size()==r.size()
                    && r.supplierStoreId().equals(p.supplierStoreId()) && r.originCountry().equals(p.originCountry()) && p.productId()!=null
                    && products.findById(p.productId()).map(product->r.category().equals(product.getCategory()) && "ALIEXPRESS".equalsIgnoreCase(product.getSupplier())).orElse(false)),"Tarifa aprovada sem SKU AliExpress na categoria/porte/fornecedor/origem informados.");
                approved.add(r);
            }
        }
        var normalized=new Document(document.costReservePercent(),document.paymentFeePercent(),document.paymentFixedFeeBrl(),document.minimumNetMarginPercent(),
            document.minimumNetProfitBrl(),document.paymentFeeEvidence(),document.profiles(),approved,document.reviewRows());
        return view(tables.saveAndFlush(new CommercialFreightTable(normalized)));
    }
    @Transactional public View publish(Long id){
        require(enabled,"Tarifas reais ainda desabilitadas. Revise a proposta de precificação antes de habilitar.");
        var table=tables.locked(id).orElseThrow(()->new com.garage.garageapi.shared.exception.ResourceNotFoundException("Tabela não encontrada"));
        require(table.getPublishedAt()==null,"Versão publicada é imutável. Salve uma nova versão.");
        var current=tables.findFirstByPublishedAtIsNotNullOrderByIdDesc();
        require(current.isEmpty()||current.get().getId()<id,"Publique uma versão mais recente.");
        require(validator.validate(table.getDocument()).isEmpty(),"Tabela sem tarifas aprovadas, custos completos ou política de rentabilidade válida.");
        require(table.getDocument().profiles().stream().map(Profile::variantId).distinct().count()==table.getDocument().profiles().size(),"SKU duplicado na tabela.");
        Instant now=Instant.now();table.getDocument().paymentFeeEvidence().requireValid(now);
        require(table.getDocument().paymentFeeEvidence().source()==Source.PAYMENT_PROVIDER_PLAN || table.getDocument().paymentFeeEvidence().source()==Source.OFFICIAL_SUPPORT,"Taxas de pagamento precisam de fonte do provedor.");
        for(var p:table.getDocument().profiles()) {p.evidence().requireValid(now);require(p.evidence().source()!=Source.PAYMENT_PROVIDER_PLAN && Arrays.asList(Locale.getISOCountries()).contains(p.originCountry()),"Origem ou fonte de custo inválida.");}
        for(var rate:table.getDocument().rates()) {rate.evidence().requireValid(now);require(rate.evidence().source()!=Source.PAYMENT_PROVIDER_PLAN && rate.packagingVerified(),"Agrupamento/capacidade ou fonte da remessa não comprovados.");}
        table.publish();return view(table);
    }
    @Transactional public View retire(Long id){var table=tables.locked(id).orElseThrow();table.retire();return view(table);}
    private View view(CommercialFreightTable t){return new View(t.getId(),t.getDocument(),t.getCreatedAt(),t.getPublishedAt(),t.isEnabled());}

    @Transactional(readOnly=true) public Decision evaluate(ShippingProvider.Request request){
        require(enabled,"Tarifa comercial não habilitada.");
        var table=tables.findFirstByPublishedAtIsNotNullOrderByIdDesc().filter(CommercialFreightTable::isEnabled)
            .orElseThrow(()->new com.garage.garageapi.shared.exception.ResourceConflictException("Sem tarifa comercial válida. Solicite cotação assistida."));
        String zip=request.zipCode().replace("-","");String state=postal.resolve(zip);
        Document d=table.getDocument();Instant now=Instant.now();d.paymentFeeEvidence().requireValid(now);
        require(d.paymentFeeEvidence().source()==Source.PAYMENT_PROVIDER_PLAN || d.paymentFeeEvidence().source()==Source.OFFICIAL_SUPPORT,"Taxas de pagamento sem fonte válida.");
        Instant until=d.paymentFeeEvidence().validUntil();
        // Aggregate before packing: duplicate cart lines can never create duplicate freight.
        Map<Long,Integer> quantities=new TreeMap<>();Map<Long,Long> owners=new HashMap<>();
        for(var item:request.items()) {
            require(item.productVariantId()!=null&&item.quantity()>0&&item.quantity()<=100,"Selecione SKU e quantidade válidos.");
            quantities.merge(item.productVariantId(),item.quantity(),Math::addExact);
            Long owner=owners.putIfAbsent(item.productVariantId(),item.productId());require(owner==null||owner.equals(item.productId()),"SKU incompatível com produto.");
        }
        require(!quantities.isEmpty()&&quantities.size()<=100,"Itens inválidos.");
        record Parcel(Rate rate,List<String> skus,int units){}
        Map<String,Parcel> parcels=new TreeMap<>();List<Object> agreedLines=new ArrayList<>();
        List<String> evidence=new ArrayList<>(List.of(d.paymentFeeEvidence().reference()));
        BigDecimal subtotal=BigDecimal.ZERO,productCost=BigDecimal.ZERO,taxes=BigDecimal.ZERO,other=BigDecimal.ZERO;
        for(var entry:quantities.entrySet()) {
            int quantity=entry.getValue();require(quantity<=100,"Quantidade acima da capacidade verificada.");
            var variant=variants.findById(entry.getKey()).orElseThrow();var product=products.findById(owners.get(entry.getKey())).orElseThrow();
            require(variant.getProduct().getId().equals(product.getId()) && Boolean.TRUE.equals(product.getActive()) && Boolean.TRUE.equals(variant.getActive())
                && "ALIEXPRESS".equalsIgnoreCase(product.getSupplier()) && "ALIEXPRESS".equalsIgnoreCase(variant.getSupplier())
                && product.getFulfillmentType()==FulfillmentType.DROPSHIPPING && product.canFulfill(quantity)
                && variant.getSupplierStockQuantity()!=null && variant.getSupplierStockQuantity()>=quantity,"SKU, estoque ou fornecedor indisponível.");
            var profile=d.profiles().stream().filter(p->p.variantId().equals(variant.getId())&&p.productId().equals(product.getId())).findFirst()
                .orElseThrow(()->new com.garage.garageapi.shared.exception.ResourceConflictException("Custo/SKU sem verificação. Solicite cotação assistida."));
            profile.evidence().requireValid(now);until=earlier(until,profile.evidence().validUntil());
            require(profile.evidence().source()!=Source.PAYMENT_PROVIDER_PLAN,"Custo do produto sem evidência AliExpress.");
            require(profile.supplierProductId().equals(product.getSupplierProductId()) && profile.supplierProductId().equals(variant.getSupplierProductId())
                && profile.supplierSkuId().equals(variant.getSupplierVariantId()),"Vínculo SKU/fornecedor mudou desde a verificação.");
            var matches=d.rates().stream().filter(r->r.state().equals(state)&&r.category().equals(product.getCategory())&&r.size()==profile.size()
                &&r.supplierStoreId().equals(profile.supplierStoreId())&&r.originCountry().equals(profile.originCountry())).toList();
            require(matches.size()==1,"Tarifa ausente ou ambígua para UF/categoria/porte/origem.");var rate=matches.get(0);
            rate.evidence().requireValid(now);require(rate.packagingVerified(),"Remessa não comprovada.");until=earlier(until,rate.evidence().validUntil());
            require(rate.evidence().source()!=Source.PAYMENT_PROVIDER_PLAN,"Frete/impostos sem evidência AliExpress.");
            BigDecimal units=BigDecimal.valueOf(quantity);subtotal=subtotal.add(money(product.getPrice()).multiply(units));
            BigDecimal unitCost=profile.supplierProductUnitBrl();
            if(variant.getSupplierCost()!=null && "BRL".equals(variant.getSupplierCostCurrency())) unitCost=unitCost.max(variant.getSupplierCost());
            productCost=productCost.add(unitCost.multiply(units));taxes=taxes.add(rate.taxesPerUnitBrl().multiply(units));other=other.add(profile.otherSupplierUnitBrl().multiply(units));
            String group=digest(List.of(rate.key(),rate.grouping().name(),rate.grouping()==Grouping.PER_SKU?variant.getId():0L));
            var existing=parcels.get(group);var skus=new ArrayList<String>(existing==null?List.of():existing.skus());skus.add(variant.getSupplierVariantId());
            parcels.put(group,new Parcel(rate,skus,quantity+(existing==null?0:existing.units())));
            agreedLines.add(List.of(product.getId(),variant.getId(),variant.getSupplierVariantId(),quantity,money(product.getPrice()).toPlainString(),rate.key()));
            evidence.add(profile.evidence().reference());evidence.add(rate.evidence().reference());
        }
        BigDecimal shipping=BigDecimal.ZERO,supplierFreight=BigDecimal.ZERO;List<ShippingProvider.Leg> legs=new ArrayList<>();int days=0;
        for(var parcel:parcels.values()) {
            Rate r=parcel.rate();int count=(parcel.units()+r.unitsPerParcel()-1)/r.unitsPerParcel();
            BigDecimal charged=money(r.customerFreightPerParcelBrl().multiply(BigDecimal.valueOf(count)));
            BigDecimal cost=money(r.supplierFreightPerParcelBrl().multiply(BigDecimal.valueOf(count)));
            shipping=shipping.add(charged);supplierFreight=supplierFreight.add(cost);days=Math.max(days,r.estimatedDays());
            legs.add(new ShippingProvider.Leg("ALIEXPRESS",r.logisticsCode(),r.logisticsName(),r.originCountry(),cost,"BRL",charged,r.estimatedDays(),List.copyOf(parcel.skus())));
        }
        BigDecimal supplier=money(productCost.add(taxes).add(other).add(supplierFreight));
        BigDecimal supplierBase=productCost.add(other).add(supplierFreight);
        int totalUnits=quantities.values().stream().mapToInt(Integer::intValue).sum();
        for(var parcel:parcels.values()) require(totalUnits<=parcel.rate().maximumVerifiedUnits()
            && supplierBase.compareTo(parcel.rate().maximumVerifiedSupplierBaseBrl())<=0,"Quantidade ou base de custo fora do domínio fiscal/logístico verificado.");
        BigDecimal reserve=supplier.multiply(d.costReservePercent()).movePointLeft(2).setScale(2,RoundingMode.CEILING);
        BigDecimal total=money(subtotal.add(shipping));BigDecimal fees=total.multiply(d.paymentFeePercent()).movePointLeft(2).add(d.paymentFixedFeeBrl()).setScale(2,RoundingMode.CEILING);
        BigDecimal profit=money(total.subtract(supplier).subtract(reserve).subtract(fees));
        require(profit.compareTo(d.minimumNetProfitBrl())>=0 && profit.multiply(new BigDecimal("100")).compareTo(total.multiply(d.minimumNetMarginPercent()))>=0,"Margem abaixo do limite comercial.");
        String code="U_"+digest(List.of(table.getId(),zip,state,agreedLines,shipping.toPlainString(),total.toPlainString(),supplier.toPlainString(),reserve.toPlainString(),fees.toPlainString()));
        var snapshot=new CommercialShippingSnapshot(table.getId(),code,state,until,supplier,reserve,fees,profit,
            parcels.values().stream().map(p->p.rate().key()).distinct().toList(),evidence.stream().distinct().toList(),null);
        return new Decision(new ShippingProvider.Option(code,"Frete inGarage por UF",money(shipping),days,"MERCHANT_UF",supplierFreight,"BRL",List.copyOf(legs)),snapshot);
    }
    @Transactional(readOnly=true) public void revalidate(Order order){
        if(order.getCommercialShipping()==null)return;
        order.requireCommercialContextUnchanged();
        var items=order.getItems().stream().map(i->new ShippingProvider.Item(i.getProductId(),i.getProductVariantId(),i.getQuantity(),i.getUnitPrice(),i.getFulfillmentType(),i.getSupplier(),i.getSupplierVariantId())).toList();
        var decision=evaluate(new ShippingProvider.Request(order.getZipCode(),items));
        require(order.getCommercialShipping().decisionCode().equals(decision.option().code())&&order.getShippingCost().compareTo(decision.option().price())==0,"Tarifa, custo ou preço mudou. Novo aceite necessário.");
    }
    private static Instant earlier(Instant a,Instant b){return a.isBefore(b)?a:b;}
    private static String digest(Object value){try{return Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsBytes(value)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
