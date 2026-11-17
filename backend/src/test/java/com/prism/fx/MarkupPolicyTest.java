package com.prism.fx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarkupPolicyTest {

    private final MarkupPolicy policy = new MarkupPolicy();

    @Test
    void markupBps_perCorridor() {
        assertEquals(350, policy.markupBps(Corridor.COP));
        assertEquals(120, policy.markupBps(Corridor.GBP));
    }

    @Test
    void stablecoinFee_is50Bps() {
        assertEquals(50, policy.stablecoinFeeBps());
    }
}
