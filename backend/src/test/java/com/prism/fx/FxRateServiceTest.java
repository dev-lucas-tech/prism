package com.prism.fx;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxRateServiceTest {

    private final FxRateService svc = new FxRateService();

    @Test
    void midRate_matchesSnapshot() {
        assertEquals(0, svc.midRate(Corridor.MXN).compareTo(new BigDecimal("0.054")));
        assertEquals(0, svc.midRate(Corridor.EUR).compareTo(new BigDecimal("1.08")));
    }

    @Test
    void settlementDrift_isDeterministic() {
        BigDecimal a = svc.midRateAtSettlement(Corridor.MXN, "tx-1");
        BigDecimal b = svc.midRateAtSettlement(Corridor.MXN, "tx-1");
        assertEquals(0, a.compareTo(b), "same input must yield same settlement rate");

        BigDecimal low = new BigDecimal("0.054").multiply(new BigDecimal("0.995"));
        BigDecimal high = new BigDecimal("0.054").multiply(new BigDecimal("1.005"));
        assertTrue(a.compareTo(low) >= 0 && a.compareTo(high) <= 0,
                () -> a + " not within ±0.5% of 0.054");
    }
}
