package com.prism.fx;

import java.math.BigDecimal;

public record FxBreakdown(
        BigDecimal estimatedCost,
        BigDecimal midMarketCost,
        BigDecimal settledCost,
        BigDecimal hiddenFxCost,
        int effectiveMarkupBps,
        BigDecimal stablecoinCost,
        BigDecimal stablecoinSavings,
        BigDecimal surpriseVsEstimate
) {
}
