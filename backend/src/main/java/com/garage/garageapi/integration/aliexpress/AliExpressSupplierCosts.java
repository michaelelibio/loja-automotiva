package com.garage.garageapi.integration.aliexpress;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Observations only. Null means unknown, never zero and never a dispatch prerequisite. */
public record AliExpressSupplierCosts(String source, String currency, BigDecimal productTotal,
        BigDecimal shippingTotal, BigDecimal taxTotal, BigDecimal total, Instant observedAt) {
    public List<String> warnings(BigDecimal saleTotal) {
        var warnings=new ArrayList<String>();
        if(productTotal==null) warnings.add("Preço dos produtos no fornecedor desconhecido.");
        if(shippingTotal==null) warnings.add("Frete do fornecedor não confirmado para o endereço do pedido.");
        if(taxTotal==null) warnings.add("Impostos do fornecedor desconhecidos.");
        if(total==null) warnings.add("Total final do fornecedor desconhecido.");
        BigDecimal known=total!=null?total:productTotal;
        if("BRL".equals(currency) && known!=null && known.compareTo(saleTotal)>0)
            warnings.add("Custo informado pelo fornecedor superior ao valor recebido na inGarage.");
        return List.copyOf(warnings);
    }
    public static AliExpressSupplierCosts unknown() { return new AliExpressSupplierCosts("UNKNOWN",null,null,null,null,null,null); }
}
