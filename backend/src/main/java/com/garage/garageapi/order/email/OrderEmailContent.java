package com.garage.garageapi.order.email;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

record OrderEmailContent(String subject, String html, String text) {
    static OrderEmailContent create(String type, OrderEmailDetails order, String baseUrl) {
        String[] copy = switch (type) {
            case "PAYMENT_APPROVED" -> new String[]{"Pagamento aprovado — GARAGE", "Pagamento aprovado", "Seu pagamento foi confirmado e o pedido seguirá para preparação."};
            case "PROCESSING" -> new String[]{"Pedido em preparação — GARAGE", "Pedido em preparação", "Seu pedido entrou em preparação."};
            case "SHIPPED" -> new String[]{"Seu pedido está a caminho — GARAGE", "Pedido enviado", "Seu pedido está a caminho."};
            case "DELIVERED" -> new String[]{"Pedido entregue — GARAGE", "Pedido entregue", "A entrega do seu pedido foi confirmada."};
            default -> throw new IllegalArgumentException("Tipo de e-mail de pedido inválido");
        };
        String url = stripSlash(baseUrl) + "/conta/pedidos/" + order.orderId();
        String safeUrl = escape(url);
        String html = """
                <!doctype html><html lang="pt-BR"><body style="margin:0;background:#111827;font-family:Arial,sans-serif;color:#111827">
                <div style="max-width:640px;margin:0 auto;padding:32px 20px"><div style="background:#fff;border-radius:12px;padding:32px">
                <h1 style="margin:0 0 20px;color:#dc2626">GARAGE</h1><h2>%s</h2><p>Olá, %s.</p><p>%s</p><p><strong>Pedido #%s</strong></p>%s
                <p style="margin:28px 0"><a href="%s" style="background:#dc2626;color:#fff;text-decoration:none;padding:13px 22px;border-radius:8px;display:inline-block">Ver pedido</a></p>
                <p>Se o botão não funcionar, copie este link:</p><p style="word-break:break-all"><a href="%s">%s</a></p>
                </div></div></body></html>
                """.formatted(copy[1], escape(order.customerName()), copy[2], order.orderId(), detailsHtml(order), safeUrl, safeUrl, safeUrl);
        String text = "GARAGE\n\n" + copy[1] + "\n\nOlá, " + order.customerName() + ".\n" + copy[2]
                + "\n\nPedido #" + order.orderId() + "\n" + detailsText(order) + "\n\nVeja seu pedido: " + url;
        return new OrderEmailContent(copy[0], html, text);
    }

    private static String detailsHtml(OrderEmailDetails order) {
        StringBuilder value = new StringBuilder("<ul>");
        order.items().forEach(item -> value.append("<li>").append(escape(item.name())).append(" — ")
                .append(item.quantity()).append(" × ").append(money(item.unitPrice())).append("</li>"));
        return value.append("</ul><p>Subtotal: ").append(money(order.subtotal())).append("<br>Frete (")
                .append(escape(order.shippingName())).append("): ").append(money(order.shippingCost()))
                .append("<br>Prazo estimado: ").append(order.shippingEstimatedDays()).append(" dias<br><strong>Total: ")
                .append(money(order.total())).append("</strong></p>").toString();
    }

    private static String detailsText(OrderEmailDetails order) {
        StringBuilder value = new StringBuilder();
        order.items().forEach(item -> value.append("- ").append(item.name()).append(": ")
                .append(item.quantity()).append(" x ").append(money(item.unitPrice())).append('\n'));
        return value.append("Subtotal: ").append(money(order.subtotal())).append("\nFrete (")
                .append(order.shippingName()).append("): ").append(money(order.shippingCost()))
                .append("\nPrazo estimado: ").append(order.shippingEstimatedDays()).append(" dias\nTotal: ")
                .append(money(order.total())).toString();
    }

    private static String money(BigDecimal value) { return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(value); }
    private static String stripSlash(String value) { return value.endsWith("/") ? value.substring(0, value.length() - 1) : value; }
    private static String escape(String value) { return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;"); }
}
