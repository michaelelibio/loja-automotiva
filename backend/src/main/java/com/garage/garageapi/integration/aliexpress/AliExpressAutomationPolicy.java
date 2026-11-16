package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.order.entity.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** A persistent configuration cutoff, never the process start time, protects historical orders. */
@Component
public class AliExpressAutomationPolicy {
    private final boolean configuredEnabled;
    private final Instant ordersCreatedFrom;

    public AliExpressAutomationPolicy(
            @Value("${aliexpress.orders.auto-dispatch-enabled:false}") boolean enabled,
            @Value("${aliexpress.orders.auto-dispatch-created-from:}") String createdFrom) {
        this.configuredEnabled = enabled;
        this.ordersCreatedFrom = createdFrom == null || createdFrom.isBlank() ? null : Instant.parse(createdFrom.trim());
    }

    public boolean enabled() { return configuredEnabled && ordersCreatedFrom != null; }
    public Instant ordersCreatedFrom() { return ordersCreatedFrom; }

    public String blockedReason(Order order) {
        if (!configuredEnabled) return "Processamento automático desativado na configuração do backend.";
        if (ordersCreatedFrom == null)
            return "Automação pausada: falta definir a data inicial dos novos pedidos. O histórico não será reenviado.";
        if (order.getCreatedAt() == null || order.getCreatedAt().isBefore(ordersCreatedFrom))
            return "Pedido anterior à ativação automática. Reconcilie o fornecedor antes de um envio manual.";
        return null;
    }
}
