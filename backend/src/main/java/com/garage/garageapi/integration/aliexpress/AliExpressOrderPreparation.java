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
        return blockedReason(order,null);
    }
    public String blockedReason(Order order, AliExpressDispatchQuote quote) {
        if (!hasAliExpress(order)) return "Este pedido não contém itens AliExpress.";
        if (aliItems(order).stream().anyMatch(i->i.getFulfillmentType()!=FulfillmentType.DROPSHIPPING))
            return "Produto AliExpress sem vínculo técnico de dropshipping válido.";
        if (!com.garage.garageapi.order.recipient.AliExpressRecipientData.status(order).complete())
            return "Dados do destinatário incompletos ou inválidos. Confira os campos pendentes no detalhe do pedido.";
        LocalDate today=LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));
        if (!order.getRecipientBirthDate().isBefore(today) || order.getRecipientBirthDate().isBefore(today.minusYears(120))
                || !order.getRecipientPhone().matches("[0-9]{10,11}")) return "Dados do destinatário inválidos para envio ao Brasil.";
        if (!text(order.getRecipientName()) || order.getRecipientName().length()>150 || !order.getZipCode().matches("[0-9]{8}")
                || !text(order.getStreet()) || !text(order.getNumber()) || !text(order.getNeighborhood()) || !text(order.getCity())
                || !Set.of("AC","AL","AP","AM","BA","CE","DF","ES","GO","MA","MT","MS","MG","PA","PB","PR","PE","PI","RJ","RN","RS","RO","RR","SC","SP","SE","TO").contains(order.getState()))
            return "Endereço de entrega incompleto ou inválido.";
        for (var item:aliItems(order)) {
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
        }
        if (!enabled || !contractConfirmed) return "Criação real bloqueada até validar o contrato AliExpress, o endereço BR e a idempotência do app.";
        if (permission == null || !permission.verified()) return "Autorização efetiva do método placeorder não verificada. Criação real bloqueada.";
        return null;
    }
    public Map<String,Object> payload(Order order,String outOrderId) {
        return payload(order,outOrderId,null);
    }
    public Map<String,Object> payload(Order order,String outOrderId,AliExpressDispatchQuote quote) {
        String reason=blockedReason(order,quote);
        if (reason!=null) throw new com.garage.garageapi.shared.exception.ResourceConflictException(reason);
        Map<String,Object> address=logisticsAddress(order);
        List<Map<String,Object>> items=new ArrayList<>();
        for (var item:aliItems(order)) {
            var variant=variants.findById(item.getProductVariantId()).orElseThrow();
            Map<String,Object> line=new LinkedHashMap<>();
            line.put("product_id",new java.math.BigInteger(item.getSupplierProductId()));line.put("product_count",item.getQuantity());
            line.put("sku_attr",variant.getRawVariantKey());
            if (quote!=null && Objects.equals(order.quoteContextDigest(),quote.contextDigest())
                    && quote.validUntil()!=null && java.time.Instant.now().isBefore(quote.validUntil())) {
                var quoted=quote.lines().stream().filter(l->l.skuId().equals(item.getSupplierVariantId()) && l.productId().equals(item.getSupplierProductId()))
                        .findFirst().orElse(null);
                if(quoted!=null && text(quoted.logisticsService()))line.put("logistics_service_name",quoted.logisticsService());
            }
            items.add(line);
        }
        return Map.of("out_order_id",outOrderId,"ds_extend_request",dsExtendRequest(order),"logistics_address",address,"product_items",items);
    }
    public static List<com.garage.garageapi.order.entity.OrderItem> aliItems(Order order) {
        return order.getItems().stream().filter(i->"ALIEXPRESS".equalsIgnoreCase(i.getSupplier())).toList();
    }
    public AliExpressDispatchQuote technicalSnapshot(Order order,String buyerHash,String appHash) {
        var lines=aliItems(order).stream().map(i->new AliExpressDispatchQuote.Line(i.getSupplierProductId(),i.getSupplierVariantId(),
                variants.findById(i.getProductVariantId()).orElseThrow().getRawVariantKey(),i.getQuantity(),null,null)).toList();
        return new AliExpressDispatchQuote(order.quoteContextDigest(),buyerHash,appHash,lines,null,null,null,null,
                java.time.Instant.now(),null,null,null);
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
    // Same BR recipient extension and date format used by the successful authorized supplier fixture.
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
