package com.prism.fx;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FxCalculatorTest {

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, actual.compareTo(new BigDecimal(expected)),
                () -> "expected " + expected + " but was " + actual);
    }

    @Test
    void settledCost_includesMarkupOverSettlementMid() {
        FxBreakdown b = FxCalculator.compute(
                new BigDecimal("1000"), new BigDecimal("0.054"), new BigDecimal("0.054"), 250, 50);

        assertMoney("55.35", b.settledCost());
        assertMoney("54.00", b.midMarketCost());
        assertMoney("1.35", b.hiddenFxCost());
        assertEquals(250, b.effectiveMarkupBps());
    }

    @Test
    void stablecoinSavings_isMarkupMinusFee() {
        FxBreakdown b = FxCalculator.compute(
                new BigDecimal("1000"), new BigDecimal("0.054"), new BigDecimal("0.054"), 250, 50);

        assertMoney("54.27", b.stablecoinCost());
        assertMoney("1.08", b.stablecoinSavings());
    }

    @Test
    void surprise_capturesSettlementDrift() {
        FxBreakdown b = FxCalculator.compute(
                new BigDecimal("1000"), new BigDecimal("0.054"), new BigDecimal("0.0543"), 250, 50);

        assertMoney("54.00", b.estimatedCost());
        // surprise is, by definition, the settled cost minus the swipe-time estimate
        assertEquals(0, b.surpriseVsEstimate().compareTo(b.settledCost().subtract(b.estimatedCost())));
        // drift pushed the real cost above the pure markup-only gap
        assertEquals(1, b.surpriseVsEstimate().compareTo(b.hiddenFxCost()));
    }

    @Test
    void copScale_doesNotRoundToZero() {
        FxBreakdown b = FxCalculator.compute(
                new BigDecimal("5000000"), new BigDecimal("0.00025"), new BigDecimal("0.00025"), 350, 50);

        assertMoney("43.75", b.hiddenFxCost());
    }

    @Test
    void zeroMarkup_hasNoHiddenCost() {
        FxBreakdown b = FxCalculator.compute(
                new BigDecimal("1000"), new BigDecimal("0.054"), new BigDecimal("0.054"), 0, 50);

        assertMoney("0.00", b.hiddenFxCost());
        assertEquals(0, b.effectiveMarkupBps());
    }
}
