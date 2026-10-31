package com.garage.garageapi.shipping.eligibility;

import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.product.entity.FulfillmentType;
import com.garage.garageapi.shared.exception.ResourceConflictException;
import com.garage.garageapi.shipping.provider.ShippingProvider;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;

/** Flat zero delivery for the administratively activated catalog. No quotations or evidence. */
@Service
public class CatalogFreeDelivery {
    public static final String CODE="CATALOG_FREE";
    public static boolean supports(String code) { return CODE.equals(code) || "VERIFIED_FREE".equals(code); }
    public ShippingProvider.Option select(List<ShippingProvider.Item> items) {
        if (items==null || items.isEmpty() || items.stream().anyMatch(i ->
                i.fulfillmentType()!=FulfillmentType.DROPSHIPPING || !"ALIEXPRESS".equalsIgnoreCase(i.supplier())
                || i.productVariantId()==null || i.quantity()<=0 || i.quantity()>1000
                || i.unitPrice()==null || i.unitPrice().signum()<=0)) throw unavailable();
        var zero=new BigDecimal("0.00");
        // A storefront delivery policy is not a supplier logistics service code.
        return new ShippingProvider.Option(CODE,"Entrega grátis",zero,0,"ALIEXPRESS",zero,"BRL",List.of());
    }
    public void validateSnapshot(Order order) {
        if (!supports(order.getShippingCode()) || order.getShippingCost().signum()!=0
                || order.getTotal().compareTo(order.getSubtotal())!=0 || order.getItems().isEmpty()
                || order.getItems().stream().anyMatch(i -> !"ALIEXPRESS".equalsIgnoreCase(i.getSupplier())
                    || i.getFulfillmentType()!=FulfillmentType.DROPSHIPPING)) throw unavailable();
    }
    private static ResourceConflictException unavailable() {
        return new ResourceConflictException("Produto indisponível para este checkout.");
    }
}
