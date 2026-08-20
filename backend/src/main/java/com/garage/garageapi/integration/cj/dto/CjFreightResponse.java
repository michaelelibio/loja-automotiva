package com.garage.garageapi.integration.cj.dto;

import java.math.BigDecimal;
import java.util.List;

public record CjFreightResponse(List<Option> options) {
    public record Option(String logisticName, String logisticAging, BigDecimal logisticPriceUsd,
                         BigDecimal taxesFeeUsd, BigDecimal clearanceFeeUsd,
                         BigDecimal totalPostageFeeUsd) { }
}
