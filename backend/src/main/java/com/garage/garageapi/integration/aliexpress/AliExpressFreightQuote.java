package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.shared.exception.ResourceConflictException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Immutable internal candidate. Not a payment authorization or a persisted quote.
 * It deliberately contains no CPF, birth date, phone or recipient name.
 */
public record AliExpressFreightQuote(Context context, List<Method> methods, Instant quotedAt) {
    public static final Duration VALIDITY = Duration.ofMinutes(10);

    public AliExpressFreightQuote {
        if (context == null || quotedAt == null || methods == null || methods.isEmpty() || methods.stream().anyMatch(Objects::isNull)) throw invalid();
        methods = List.copyOf(methods);
        if (methods.stream().map(Method::serviceName).distinct().count() != methods.size()) throw invalid();
    }

    public Method select(Context current, String serviceName, Instant now) {
        if (!context.equals(current)) throw new ResourceConflictException("Itens ou endereço alterados. Recalcule o frete.");
        if (now == null || now.isBefore(quotedAt) || !now.isBefore(quotedAt.plus(VALIDITY)))
            throw new ResourceConflictException("Cotação expirada. Recalcule o frete.");
        return methods.stream().filter(method -> method.serviceName().equals(serviceName)).findFirst()
                .orElseThrow(AliExpressFreightQuote::invalid);
    }

    /** Compares against a fresh candidate, without changing the paid snapshot. */
    public void requireUnchanged(String serviceName, AliExpressFreightQuote refreshed, Instant now) {
        if (refreshed == null) throw invalid();
        Method paid = methods.stream().filter(method -> method.serviceName().equals(serviceName)).findFirst()
                .orElseThrow(AliExpressFreightQuote::invalid);
        Method latest = refreshed.select(context, serviceName, now);
        if (!paid.currency().equals(latest.currency()) || paid.amount().compareTo(latest.amount()) != 0)
            throw new ResourceConflictException("O frete mudou. É necessária revisão administrativa antes do envio.");
    }

    public record Context(List<Line> items, Destination destination) {
        public Context {
            if (items == null || items.isEmpty() || destination == null || items.stream().anyMatch(Objects::isNull)) throw invalid();
            items = items.stream().sorted(Comparator.comparing(Line::productId).thenComparing(Line::variantId)).toList();
            if (items.stream().map(line -> line.productId() + ":" + line.variantId()).distinct().count() != items.size()) throw invalid();
        }
        @Override public String toString() { return "FreightContext[redacted]"; }
    }

    public record Line(String productId, String variantId, int quantity, String originCountry) {
        public Line {
            if (productId == null || !productId.matches("[0-9]{8,30}") || variantId == null
                    || !variantId.matches("[0-9]{1,30}") || quantity < 1 || quantity > 1000
                    || originCountry == null || !originCountry.matches("[A-Z]{2}")) throw invalid();
        }
    }

    public record Destination(String country, String zipCode, String state, String city,
                              String street, String number, String neighborhood, String complement) {
        public Destination {
            if (!"BR".equals(country) || zipCode == null || !zipCode.matches("[0-9]{8}")
                    || state == null || !Set.of("AC","AL","AP","AM","BA","CE","DF","ES","GO","MA","MT","MS","MG","PA","PB","PR","PE","PI","RJ","RN","RS","RO","RR","SC","SP","SE","TO").contains(state)
                    || !text(city) || !text(street) || !text(number) || !text(neighborhood)) throw invalid();
        }
        @Override public String toString() { return "FreightDestination[redacted]"; }
    }

    public record Method(String serviceName, BigDecimal amount, String currency,
                         Integer estimatedMinDays, Integer estimatedMaxDays) {
        public Method {
            if (!text(serviceName) || serviceName.length() > 120 || serviceName.chars().anyMatch(Character::isISOControl)
                    || amount == null || amount.signum() < 0 || amount.stripTrailingZeros().scale() > 2
                    || amount.precision() - amount.scale() > 10 || !"BRL".equals(currency)
                    || (estimatedMinDays == null) != (estimatedMaxDays == null)
                    || (estimatedMinDays != null && (estimatedMinDays < 1 || estimatedMaxDays < estimatedMinDays))) throw invalid();
        }
    }

    @Override public String toString() { return "AliExpressFreightQuote[redacted]"; }
    private static boolean text(String value) { return value != null && !value.isBlank(); }
    private static ResourceConflictException invalid() { return new ResourceConflictException("Não foi possível confirmar a cotação de frete AliExpress."); }
}
