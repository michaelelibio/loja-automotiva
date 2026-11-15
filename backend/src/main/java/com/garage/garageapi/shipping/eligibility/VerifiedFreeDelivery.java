package com.garage.garageapi.shipping.eligibility;

import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.shipping.provider.ShippingProvider;
import com.garage.garageapi.product.entity.FulfillmentType;
import com.garage.garageapi.shared.exception.ResourceConflictException;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Historical helper retained in isolation; no Spring bean or checkout dependency. */
public class VerifiedFreeDelivery {
    private final List<Evidence> evidence;
    public VerifiedFreeDelivery(String json) {
        try { evidence = List.of(JsonMapper.builder().build().readValue(json, Evidence[].class)); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("Evidência de entrega inválida."); }
    }
    public record Evidence(String reference, String addressDigest, Instant confirmedAt, Instant validUntil,
                           BigDecimal supplierShippingAmount, String currency, List<Line> items) { }
    public record Line(Long productId, Long variantId, String supplierProductId, String skuId, int quantity, BigDecimal unitPrice,
                       String logisticsCode, String logisticsName, String originCountry, int estimatedDays) { }

    public Set<Long> evidencedProductIds() {
        Set<Long> ids = new HashSet<>();
        evidence.stream().filter(e -> valid(e) && e.items() != null)
                .flatMap(e -> e.items().stream()).filter(Objects::nonNull).map(Line::productId)
                .filter(Objects::nonNull).forEach(ids::add);
        return ids;
    }

    public ShippingProvider.Option confirm(Address address, List<ShippingProvider.Item> items) {
        if (items == null || items.isEmpty() || items.stream().anyMatch(i ->
                i.fulfillmentType() != FulfillmentType.DROPSHIPPING || !"ALIEXPRESS".equalsIgnoreCase(i.supplier())
                || i.productVariantId() == null || i.quantity() <= 0 || i.quantity() > 1000)) throw unavailable();
        String digest = addressDigest(address);
        Evidence match = evidence.stream().filter(e -> valid(e) && digest.equals(e.addressDigest()) && matches(e.items(), items))
                .findFirst().orElseThrow(VerifiedFreeDelivery::unavailable);
        var zero = new BigDecimal("0.00");
        var legs = match.items().stream().map(l -> new ShippingProvider.Leg("ALIEXPRESS", l.logisticsCode(),
                l.logisticsName(), l.originCountry(), zero, "BRL", zero, l.estimatedDays(), List.of(l.skuId()))).toList();
        return new ShippingProvider.Option("VERIFIED_FREE", "Entrega grátis", zero,
                match.items().stream().mapToInt(Line::estimatedDays).max().orElse(0), "ALIEXPRESS", zero, "BRL", legs);
    }
    public void confirm(com.garage.garageapi.order.entity.Order order) {
        var address = new Address(null,null,order.getRecipientName(),order.getZipCode(),order.getStreet(),order.getNumber(),
                order.getComplement(),order.getNeighborhood(),order.getCity(),order.getState(),false);
        var items = order.getItems().stream().map(i -> new ShippingProvider.Item(i.getProductId(),i.getProductVariantId(),
                i.getQuantity(),i.getUnitPrice(),i.getFulfillmentType(),i.getSupplier(),i.getSupplierVariantId(),i.getSupplierProductId())).toList();
        var confirmed = confirm(address,items);
        if (order.getShippingCost().signum() != 0 || order.getTotal().compareTo(order.getSubtotal()) != 0
                || !confirmed.legs().equals(order.getShippingLegs())) throw unavailable();
    }
    public Instant validUntil(Address address, List<ShippingProvider.Item> items) {
        String digest = addressDigest(address);
        return evidence.stream().filter(e -> valid(e) && digest.equals(e.addressDigest()) && matches(e.items(),items))
                .map(Evidence::validUntil).findFirst().orElseThrow(VerifiedFreeDelivery::unavailable);
    }
    private static boolean matches(List<Line> lines, List<ShippingProvider.Item> items) {
        if (lines == null || lines.size() != items.size()) return false;
        Set<Long> variants = new HashSet<>();
        Set<String> skus = new HashSet<>();
        for (Line l : lines) {
            if (l == null || l.variantId() == null || !variants.add(l.variantId()) || !text(l.supplierProductId()) || !text(l.skuId()) || !skus.add(l.skuId())
                    || !text(l.logisticsCode()) || !text(l.logisticsName()) || !text(l.originCountry())
                    || l.estimatedDays() < 0 || l.unitPrice() == null || l.unitPrice().signum() <= 0
                    || items.stream().noneMatch(i -> Objects.equals(l.productId(), i.productId())
                    && l.variantId().equals(i.productVariantId()) && l.skuId().equals(i.supplierVariantId())
                    && l.supplierProductId().equals(i.supplierProductId())
                    && l.quantity() == i.quantity() && l.unitPrice().compareTo(i.unitPrice()) == 0)) return false;
        }
        return true;
    }
    public static String addressDigest(Address a) {
        try {
            var bytes = JsonMapper.builder().build().writeValueAsBytes(Arrays.asList("BR", a.getRecipientName(),
                    a.getZipCode(), a.getStreet(), a.getNumber(), a.getComplement(), a.getNeighborhood(), a.getCity(), a.getState()));
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static boolean text(String s) { return s != null && !s.isBlank(); }
    private static boolean valid(Evidence e) {
        Instant now = Instant.now();
        return e != null && text(e.reference()) && e.addressDigest() != null && e.addressDigest().matches("[a-f0-9]{64}")
                && e.confirmedAt() != null && !e.confirmedAt().isAfter(now) && e.validUntil() != null
                && e.validUntil().isAfter(now) && e.validUntil().isAfter(e.confirmedAt())
                && e.supplierShippingAmount() != null && e.supplierShippingAmount().signum() == 0 && "BRL".equals(e.currency());
    }
    private static ResourceConflictException unavailable() {
        return new ResourceConflictException("Entrega grátis não confirmada para estes produtos, quantidades e endereço. Compra indisponível.");
    }
}
