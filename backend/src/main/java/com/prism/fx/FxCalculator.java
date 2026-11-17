package com.prism.fx;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure FX decomposition. Splits a card purchase's settled cost back into its
 * mid-market value and the hidden markup, and compares it to a stablecoin-corridor path.
 */
public final class FxCalculator {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode RM = RoundingMode.HALF_EVEN;
    private static final BigDecimal BPS_DIVISOR = new BigDecimal("10000");

    private FxCalculator() {
    }

    public static FxBreakdown compute(BigDecimal amount,
                                      BigDecimal midAuth,
                                      BigDecimal midSettle,
                                      int markupBps,
                                      int stablecoinFeeBps) {
        BigDecimal markup = BigDecimal.valueOf(markupBps).divide(BPS_DIVISOR);
        BigDecimal stableFee = BigDecimal.valueOf(stablecoinFeeBps).divide(BPS_DIVISOR);

        BigDecimal estimatedCost = money(amount.multiply(midAuth));
        BigDecimal midMarketCost = money(amount.multiply(midSettle));
        BigDecimal settledCost = money(amount.multiply(midSettle).multiply(BigDecimal.ONE.add(markup)));
        BigDecimal stablecoinCost = money(amount.multiply(midSettle).multiply(BigDecimal.ONE.add(stableFee)));

        BigDecimal hiddenFxCost = settledCost.subtract(midMarketCost);
        BigDecimal stablecoinSavings = settledCost.subtract(stablecoinCost);
        BigDecimal surpriseVsEstimate = settledCost.subtract(estimatedCost);

        int effectiveMarkupBps;
        if (midMarketCost.signum() == 0) {
            effectiveMarkupBps = 0;
        } else {
            BigDecimal ratio = settledCost.divide(midMarketCost, 10, RM).subtract(BigDecimal.ONE);
            effectiveMarkupBps = ratio.multiply(BPS_DIVISOR).setScale(0, RM).intValueExact();
        }

        return new FxBreakdown(estimatedCost, midMarketCost, settledCost, hiddenFxCost,
                effectiveMarkupBps, stablecoinCost, stablecoinSavings, surpriseVsEstimate);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RM);
    }
}
