package com.garage.garageapi.integration.cj.currency;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class ConfiguredExchangeRateService implements ExchangeRateService {
    private final BigDecimal usdToBrl;

    public ConfiguredExchangeRateService(
            @Value("${app.currency.usd-brl-rate}") BigDecimal usdToBrl) {
        if (usdToBrl == null || usdToBrl.signum() <= 0) {
            throw new IllegalArgumentException("Cotação USD/BRL deve ser maior que zero");
        }
        this.usdToBrl = usdToBrl;
    }

    @Override
    public BigDecimal usdToBrl() {
        return usdToBrl;
    }
}
