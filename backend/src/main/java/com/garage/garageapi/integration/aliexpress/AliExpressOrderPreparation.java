package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.product.entity.FulfillmentType;
import com.garage.garageapi.product.repository.ProductVariantRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.*;

@Component
public class AliExpressOrderPreparation {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AliExpressPlaceOrderPermission permission;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.garage.garageapi.shipping.eligibility.CatalogFreeDelivery freeDelivery;
    private final ProductVariantRepository variants;
    private final boolean enabled, contractConfirmed;
    public AliExpressOrderPreparation(ProductVariantRepository variants,
            @Value("${aliexpress.orders.enabled:false}") boolean enabled,
            @Value("${aliexpress.orders.contract-confirmed:false}") boolean contractConfirmed) {
        this.variants=variants; this.enabled=enabled; this.contractConfirmed=contractConfirmed;
    }
    public boolean hasAliExpress(Order order) {
        return order.getItems().stream().anyMatch(i -> "ALIEXPRESS".equalsIgnoreCase(i.getSupplier()));
    }
    public String blockedReason(Order order) {
        if (order.getItems().isEmpty() || order.getItems().stream().anyMatch(i ->
                !"ALIEXPRESS".equalsIgnoreCase(i.getSupplier()) || i.getFulfillmentType()!=FulfillmentType.DROPSHIPPING))
            return "Envio disponível somente para pedidos compostos exclusivamente por itens AliExpress.";
        if (!com.garage.garageapi.order.recipient.AliExpressRecipientData.status(order).complete())
            return "Dados do destinatário incompletos ou inválidos. Confira os campos pendentes no detalhe do pedido.";
        LocalDate today=LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));
        if (!order.getRecipientBirthDate().isBefore(today) || order.getRecipientBirthDate().isBefore(today.minusYears(120))
                || !order.getRecipientPhone().matches("[0-9]{10,11}")) return "Dados do destinatário inválidos para envio ao Brasil.";
        if (!text(order.getRecipientName()) || order.getRecipientName().length()>150 || !order.getZipCode().matches("[0-9]{8}")
                || !text(order.getStreet()) || !text(order.getNumber()) || !text(order.getNeighborhood()) || !text(order.getCity())
                || !Set.of("AC","AL","AP","AM","BA","CE","DF","ES","GO","MA","MT","MS","MG","PA","PB","PR","PE","PI","RJ","RN","RS","RO","RR","SC","SP","SE","TO").contains(order.getState()))
            return "Endereço de entrega incompleto ou inválido.";
        if (order.getAssistedQuote()!=null) {
            try { order.requireAssistedDispatch(); }
            catch (com.garage.garageapi.shared.exception.ResourceConflictException ex) { return ex.getMessage(); }
            return "Use o encaminhamento manual da cotação assistida; criação via API depende do contrato validado.";
        }
        var legs=order.getShippingLegs();
        boolean catalogFree=com.garage.garageapi.shipping.eligibility.CatalogFreeDelivery.CODE.equals(order.getShippingCode());
        if ((!catalogFree && legs.isEmpty()) || legs.stream().anyMatch(leg -> !"ALIEXPRESS".equals(leg.provider())
                || !text(leg.name()) || !text(leg.code()) || !text(leg.originCountry()) || leg.amount()==null
                || leg.amount().signum()<0 || !"BRL".equals(leg.currency()) || leg.supplierVariantIds()==null
                || leg.supplierVariantIds().isEmpty()))
            return "Modalidade e custo de frete AliExpress ainda não foram confirmados para este pedido.";
        Set<String> skuIds=new HashSet<>();
        for (var item:order.getItems()) {
            if (item.getQuantity()==null || item.getQuantity()<=0 || item.getQuantity()>1000
                    || item.getSupplierProductId()==null || !item.getSupplierProductId().matches("[0-9]{8,30}")
                    || item.getSupplierVariantId()==null || !item.getSupplierVariantId().matches("[0-9]{1,30}") || item.getProductVariantId()==null)
                return "Quantidade ou identificadores AliExpress inválidos no pedido.";
            var variant=variants.findById(item.getProductVariantId()).orElse(null);
            if (variant==null || !"ALIEXPRESS".equalsIgnoreCase(variant.getSupplier())
                    || !Objects.equals(variant.getProduct().getId(),item.getProductId())
                    || !item.getSupplierProductId().equals(variant.getSupplierProductId())
                    || !item.getSupplierVariantId().equals(variant.getSupplierVariantId()) || !text(variant.getRawVariantKey()))
                return "Identificadores originais da variante ausentes ou divergentes. Não é seguro criar a compra.";
            skuIds.add(item.getSupplierVariantId());
        }
        List<String> quotedSkus=legs.stream().flatMap(leg->leg.supplierVariantIds().stream()).toList();
        if (!catalogFree && (new HashSet<>(quotedSkus).size()!=quotedSkus.size() || !skuIds.equals(new HashSet<>(quotedSkus))))
            return "O frete confirmado não corresponde às variantes do pedido.";
        if (com.garage.garageapi.shipping.eligibility.CatalogFreeDelivery.supports(order.getShippingCode())) {
            try {
                if (freeDelivery == null) return "Entrega grátis não confirmada.";
                freeDelivery.validateSnapshot(order);
            } catch (com.garage.garageapi.shared.exception.ResourceConflictException ex) { return ex.getMessage(); }
        }
        if (!enabled || !contractConfirmed) return "Criação real bloqueada até validar o contrato AliExpress, o endereço BR e a idempotência do app.";
        if (permission == null || !permission.verified()) return "Autorização efetiva do método placeorder não verificada. Criação real bloqueada.";
        return null;
    }
    public Map<String,Object> payload(Order order,String outOrderId) {
        String reason=blockedReason(order);
        if (reason!=null) throw new com.garage.garageapi.shared.exception.ResourceConflictException(reason);
        Map<String,Object> address=logisticsAddress(order);
        List<Map<String,Object>> items=new ArrayList<>();
        for (var item:order.getItems()) {
            var variant=variants.findById(item.getProductVariantId()).orElseThrow();
            Map<String,Object> line=new LinkedHashMap<>();
            line.put("product_id",new java.math.BigInteger(item.getSupplierProductId()));line.put("product_count",item.getQuantity());
            line.put("sku_attr",variant.getRawVariantKey());
            order.getShippingLegs().stream().filter(leg->leg.supplierVariantIds().contains(item.getSupplierVariantId()))
                    .findFirst().ifPresent(leg->line.put("logistics_service_name",leg.code()));
            items.add(line);
        }
        return Map.of("logistics_address",address,"product_items",items);
    }
    // Provider representation only: the persisted snapshot stays a LocalDate, never a formatted string.
    static Map<String,Object> logisticsAddress(Order order) {
        Map<String,Object> address=new LinkedHashMap<>();
        address.put("country","BR");address.put("province",provinceName(order.getState()));address.put("city",order.getCity());
        address.put("address",order.getStreet()+", "+order.getNumber());
        address.put("address2",order.getNeighborhood()+(text(order.getComplement()) ? " - "+order.getComplement() : ""));
        address.put("zip",order.getZipCode());address.put("contact_person",order.getRecipientName());address.put("full_name",order.getRecipientName());
        address.put("cpf",order.getRecipientCpf());
        address.put("mobile_no",order.getRecipientPhone());address.put("phone_country","+55");address.put("locale","pt_BR");
        return address;
    }
    // Historical helper only. This unverified extension is excluded from the placeorder payload.
    static Map<String,Object> dsExtendRequest(Order order) {
        return Map.of("trade_extra_param",Map.of("birthday",order.getRecipientBirthDate()
                .format(java.time.format.DateTimeFormatter.ofPattern("ddMMyyyy"))));
    }
    private static String provinceName(String uf) {
        return switch(uf) {
            case "AC"->"Acre"; case "AL"->"Alagoas"; case "AP"->"Amapá"; case "AM"->"Amazonas";
            case "BA"->"Bahia"; case "CE"->"Ceará"; case "DF"->"Distrito Federal"; case "ES"->"Espírito Santo";
            case "GO"->"Goiás"; case "MA"->"Maranhão"; case "MT"->"Mato Grosso"; case "MS"->"Mato Grosso do Sul";
            case "MG"->"Minas Gerais"; case "PA"->"Pará"; case "PB"->"Paraíba"; case "PR"->"Paraná";
            case "PE"->"Pernambuco"; case "PI"->"Piauí"; case "RJ"->"Rio de Janeiro"; case "RN"->"Rio Grande do Norte";
            case "RS"->"Rio Grande do Sul"; case "RO"->"Rondônia"; case "RR"->"Roraima"; case "SC"->"Santa Catarina";
            case "SP"->"São Paulo"; case "SE"->"Sergipe"; case "TO"->"Tocantins";
            default->throw new IllegalArgumentException("UF inválida");
        };
    }
    private static boolean text(String value) { return value!=null && !value.isBlank(); }
}
