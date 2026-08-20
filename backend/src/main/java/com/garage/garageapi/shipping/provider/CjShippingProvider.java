package com.garage.garageapi.shipping.provider;

import com.garage.garageapi.integration.cj.currency.ExchangeRateService;
import com.garage.garageapi.integration.cj.dto.CjFreightResponse;
import com.garage.garageapi.integration.cj.service.CjCommerceService;
import com.garage.garageapi.integration.cj.exception.CjIntegrationException;
import com.garage.garageapi.shipping.availability.ProductAvailabilityProvider;
import com.garage.garageapi.shipping.exception.DropshippingUnavailableException;
import com.garage.garageapi.shipping.exception.InvalidShippingOptionException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CjShippingProvider {
    private static final Pattern DAYS = Pattern.compile("(\\d+)(?:\\D+(\\d+))?");
    private final ProductAvailabilityProvider availabilityProvider;
    private final CjCommerceService commerceService;
    private final ExchangeRateService exchangeRateService;

    public CjShippingProvider(ProductAvailabilityProvider availabilityProvider,
                              CjCommerceService commerceService,
                              ExchangeRateService exchangeRateService) {
        this.availabilityProvider = availabilityProvider;
        this.commerceService = commerceService;
        this.exchangeRateService = exchangeRateService;
    }

    public List<ShippingProvider.Option> quote(String zipCode, List<ShippingProvider.Item> items) {
        if (items.isEmpty()) return List.of();
        Set<String> commonOrigins = null;
        for (ShippingProvider.Item item : items) {
            if (item.supplierVariantId() == null || item.supplierVariantId().isBlank()) {
                throw new DropshippingUnavailableException(
                        "A opção selecionada não está disponível para entrega.");
            }
            var availability = availabilityProvider.check(item.supplierVariantId(), item.quantity());
            Set<String> origins = new LinkedHashSet<>();
            availability.warehouses().stream()
                    .filter(warehouse -> warehouse.availableQuantity() >= item.quantity())
                    .map(ProductAvailabilityProvider.Warehouse::countryCode)
                    .filter(code -> code != null && !code.isBlank())
                    .forEach(origins::add);
            if (!availability.available() || origins.isEmpty()) {
                throw new DropshippingUnavailableException(
                        "Uma das opções selecionadas está indisponível no momento.");
            }
            if (commonOrigins == null) commonOrigins = new LinkedHashSet<>(origins);
            else commonOrigins.retainAll(origins);
        }
        if (commonOrigins == null || commonOrigins.isEmpty()) {
            throw new DropshippingUnavailableException(
                    "Os itens selecionados exigem envios de origens diferentes e ainda não podem ser comprados juntos.");
        }

        List<Map<String, Object>> products = items.stream().map(item -> {
            Map<String, Object> product = new LinkedHashMap<>();
            product.put("quantity", item.quantity());
            product.put("vid", item.supplierVariantId());
            return product;
        }).toList();
        BigDecimal exchangeRate = exchangeRateService.usdToBrl();
        if (exchangeRate == null || exchangeRate.signum() <= 0) {
            throw new CjIntegrationException("Conversão do frete CJ não está configurada",
                    CjIntegrationException.Reason.NOT_CONFIGURED);
        }

        List<ShippingProvider.Option> result = new ArrayList<>();
        commonOrigins.stream().sorted().forEach(origin -> {
            CjFreightResponse response = commerceService.freight(origin, "BR", zipCode, products);
            for (CjFreightResponse.Option source : response.options()) {
                BigDecimal usd = source.totalPostageFeeUsd() != null
                        ? source.totalPostageFeeUsd() : source.logisticPriceUsd();
                if (usd == null || usd.signum() < 0) {
                    throw new CjIntegrationException("Resposta inválida da CJ durante cotação de frete",
                            CjIntegrationException.Reason.INVALID_RESPONSE);
                }
                int estimatedDays = maxDays(source.logisticAging());
                BigDecimal brl = usd.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
                String code = "CJ-" + origin + "-" + digest(source.logisticName());
                List<String> variantIds = items.stream().map(ShippingProvider.Item::supplierVariantId)
                        .distinct().sorted().toList();
                var leg = new ShippingProvider.Leg("CJ", source.logisticName(),
                        source.logisticName(), origin, usd, "USD", brl, estimatedDays, variantIds);
                result.add(new ShippingProvider.Option(code, source.logisticName(), brl,
                        estimatedDays, "CJ", usd, "USD", List.of(leg)));
            }
        });
        if (result.isEmpty()) {
            throw new InvalidShippingOptionException(
                    "Nenhum método logístico da CJ está disponível para este endereço.");
        }
        return result.stream().sorted(Comparator.comparing(ShippingProvider.Option::price)
                .thenComparing(ShippingProvider.Option::code)).toList();
    }

    private int maxDays(String aging) {
        Matcher matcher = DAYS.matcher(aging == null ? "" : aging);
        if (!matcher.find()) {
            throw invalidFreightResponse();
        }
        String maximum = matcher.group(2) == null ? matcher.group(1) : matcher.group(2);
        try {
            int days = Integer.parseInt(maximum);
            if (days < 1) throw invalidFreightResponse();
            return days;
        } catch (NumberFormatException exception) {
            throw invalidFreightResponse();
        }
    }

    private CjIntegrationException invalidFreightResponse() {
        return new CjIntegrationException("Resposta inválida da CJ durante cotação de frete",
                CjIntegrationException.Reason.INVALID_RESPONSE);
    }

    private String digest(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < 6; index++) result.append(String.format("%02x", hash[index]));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
