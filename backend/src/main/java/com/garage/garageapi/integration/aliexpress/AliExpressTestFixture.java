package com.garage.garageapi.integration.aliexpress;

import java.math.BigDecimal;
import java.util.*;

/** Snapshot of the owner's authorized order #19; never populated from purchase request data. */
public record AliExpressTestFixture(String id,String authorizationReference,String productId,String skuId,
        String skuAttr,int quantity,String deliveryService,String currency,BigDecimal productCost,
        BigDecimal shippingCost,BigDecimal taxCost,BigDecimal totalCost,Map<String,String> address,java.time.LocalDate birthDate) {
    public AliExpressTestFixture {address=address==null?null:Map.copyOf(address);}
    public boolean valid() {
        return id!=null && id.matches("[a-zA-Z0-9_-]{1,80}") && text(authorizationReference)
                && productId!=null && productId.matches("[0-9]{8,30}") && skuId!=null && skuId.matches("[0-9]{1,30}") && text(skuAttr)
                && quantity>0 && quantity<=10 && (deliveryService==null || text(deliveryService)) && "BRL".equals(currency)
                && money(productCost) && money(shippingCost) && money(taxCost) && money(totalCost)
                && (totalCost==null || (productCost!=null && shippingCost!=null && taxCost!=null && productCost.add(shippingCost).add(taxCost).compareTo(totalCost)==0))
                && com.garage.garageapi.order.recipient.AliExpressRecipientData.birthDateValid(birthDate)
                && address!=null && "BR".equals(address.get("country"))
                && com.garage.garageapi.order.recipient.AliExpressRecipientData.cpfValid(address.get("cpf"))
                && com.garage.garageapi.order.recipient.AliExpressRecipientData.phoneValid(address.get("mobile_no"))
                && address.get("zip")!=null && address.get("zip").matches("[0-9]{8}")
                && "+55".equals(address.get("phone_country")) && "pt_BR".equals(address.get("locale"))
                && Set.of("country","province","city","address","address2","zip","contact_person","full_name","cpf","mobile_no","phone_country","locale").containsAll(address.keySet())
                && List.of("province","city","address","zip","contact_person","full_name","cpf","mobile_no").stream().allMatch(k->text(address.get(k)));
    }
    public Map<String,Object> payload() {
        if(!valid())throw new IllegalStateException("Dados obrigatórios do pedido inválidos.");
        Map<String,Object> item=new LinkedHashMap<>();item.put("product_id",new java.math.BigInteger(productId));item.put("product_count",quantity);item.put("sku_attr",skuAttr);
        if(deliveryService!=null)item.put("logistics_service_name",deliveryService);
        return Map.of("out_order_id",id,"ds_extend_request",Map.of("trade_extra_param",Map.of("birthday",birthDate.format(java.time.format.DateTimeFormatter.ofPattern("ddMMyyyy")))),
                "logistics_address",address,"product_items",List.of(item));
    }
    public AliExpressTestFixture withProductCost(BigDecimal cost){return new AliExpressTestFixture(id,authorizationReference,productId,skuId,skuAttr,quantity,deliveryService,currency,cost,shippingCost,taxCost,
            cost!=null && shippingCost!=null && taxCost!=null?cost.add(shippingCost).add(taxCost):null,address,birthDate);}
    public String payloadDigest(){return digestOf(Arrays.asList(id,productId,skuId,skuAttr,quantity,deliveryService,new TreeMap<>(address),birthDate));}
    public String digest(){return digestOf(Arrays.asList(payloadDigest(),currency,decimal(productCost),decimal(shippingCost),decimal(taxCost),decimal(totalCost)));}
    static String digestOf(Object value){return AliExpressOAuthStateService.hash(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(value));}
    private static String decimal(BigDecimal n){return n==null?null:n.stripTrailingZeros().toPlainString();}
    private static boolean money(BigDecimal n){return n==null || n.signum()>=0;}
    private static boolean text(String value){return value!=null && !value.isBlank() && value.length()<=500;}
    @Override public String toString(){return "AliExpressTestFixture[REDACTED]";}
}
