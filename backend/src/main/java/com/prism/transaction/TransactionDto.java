package com.prism.transaction;

import com.prism.fx.Corridor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A transaction plus its FX decomposition. For a not-yet-settled transaction only
 * {@code estimatedCost} is populated; the settled fields are null until settlement.
 */
public record TransactionDto(
        String id,
        String employee,
        String merchant,
        String merchantCountry,
        Corridor corridor,
        BigDecimal purchaseAmount,
        String fundingCurrency,
        TransactionStatus status,
        Instant authorizedAt,
        Instant settledAt,
        BigDecimal midAuthRate,
        BigDecimal midSettleRate,
        BigDecimal estimatedCost,
        BigDecimal midMarketCost,
        BigDecimal settledCost,
        BigDecimal hiddenFxCost,
        Integer effectiveMarkupBps,
        BigDecimal stablecoinCost,
        BigDecimal stablecoinSavings,
        BigDecimal surpriseVsEstimate
) {
}
