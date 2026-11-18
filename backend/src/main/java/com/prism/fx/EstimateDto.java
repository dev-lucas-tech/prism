package com.prism.fx;

import java.math.BigDecimal;

public record EstimateDto(
        String corridor,
        String country,
        BigDecimal merchantAmount,
        BigDecimal midRate,
        BigDecimal midMarketCost,
        BigDecimal estimatedCost,
        BigDecimal estimatedHiddenFxCost,
        int effectiveMarkupBps,
        int stablecoinFeeBps,
        BigDecimal stablecoinCost,
        BigDecimal stablecoinSavings
) {
}
