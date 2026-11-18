package com.prism.fx;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EstimateServiceTest {

    private final EstimateService service = new EstimateService(new FxRateService(), new MarkupPolicy());

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, actual.compareTo(new BigDecimal(expected)),
                () -> "expected " + expected + " but was " + actual);
    }

    @Test
    void estimatesBrlDecomposition() {
        EstimateDto dto = service.estimate(Corridor.BRL, new BigDecimal("1800"));

        assertEquals("BRL", dto.corridor());
        assertEquals("Brazil", dto.country());
        assertMoney("333.00", dto.midMarketCost());
        assertMoney("342.99", dto.estimatedCost());
        assertMoney("9.99", dto.estimatedHiddenFxCost());
        assertEquals(300, dto.effectiveMarkupBps());
        assertEquals(50, dto.stablecoinFeeBps());
        assertMoney("334.66", dto.stablecoinCost());
        assertMoney("8.33", dto.stablecoinSavings());
    }

    @Test
    void copEstimateKeepsHiddenCostNonZero() {
        EstimateDto dto = service.estimate(Corridor.COP, new BigDecimal("4000000"));

        assertTrue(dto.estimatedHiddenFxCost().compareTo(new BigDecimal("30")) > 0,
                () -> "expected COP hidden cost > 30 but was " + dto.estimatedHiddenFxCost());
    }
}
