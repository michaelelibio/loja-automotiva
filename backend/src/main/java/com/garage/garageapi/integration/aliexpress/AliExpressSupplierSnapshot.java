package com.garage.garageapi.integration.aliexpress;

import java.time.Instant;
import java.util.List;

/** Only allowlisted status codes, never supplier payloads, credentials or recipient data. */
public record AliExpressSupplierSnapshot(String token, Instant startedAt, Instant checkedAt, List<Part> parts) {
    public record View(Instant startedAt, Instant checkedAt, boolean inProgress, List<Part> parts) { }
    public View view() { return new View(startedAt,checkedAt,token!=null,parts); }
    public record Part(String orderId, String state, String orderStatus, String logisticsStatus,
                       boolean verified, Instant checkedAt, String failureCode) { }
    public static Part unknown(String id, String code) {
        return new Part(id,"UNKNOWN",null,null,false,Instant.now(),code);
    }
    public static Part classify(String id, String order, String logistics) {
        String state = switch (order == null ? "" : order) {
            case "PLACE_ORDER_SUCCESS" -> "AWAITING_MANUAL_PAYMENT";
            case "RISK_CONTROL" -> "PAYMENT_CONFIRMED";
            case "WAIT_SELLER_SEND_GOODS" -> "AWAITING_SHIPMENT";
            case "WAIT_BUYER_ACCEPT_GOODS" -> "SHIPPED";
            case "SELLER_PART_SEND_GOODS" -> "PARTIALLY_SHIPPED";
            case "IN_CANCEL" -> "FAILED";
            default -> "UNKNOWN";
        };
        // Unrecognized/contradictory combinations cannot establish a paid or shipped order.
        if ("PLACE_ORDER_SUCCESS".equals(order) && "SELLER_SEND_GOODS".equals(logistics)) state="UNKNOWN";
        boolean known = !"UNKNOWN".equals(state);
        return new Part(id,state,safe(order),safe(logistics),known,Instant.now(),known ? null : "UNRECOGNIZED_STATUS");
    }
    private static String safe(String code) {
        return code!=null && java.util.Set.of("PLACE_ORDER_SUCCESS","RISK_CONTROL","WAIT_SELLER_SEND_GOODS",
                "WAIT_BUYER_ACCEPT_GOODS","SELLER_PART_SEND_GOODS","IN_CANCEL","PAYMENT_PROCESSING",
                "WAIT_SELLER_EXAMINE_MONEY","FINISH","FUND_PROCESSING","ARCHIVE","IN_ISSUE","IN_FROZEN",
                "RISK_CONTROL_HOLD","SELLER_SEND_GOODS","NO_LOGISTICS","BUYER_ACCEPT_GOODS").contains(code) ? code : null;
    }
}
