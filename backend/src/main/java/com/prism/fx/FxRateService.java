package com.prism.fx;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Bundled mid-market FX snapshot. The authorization rate is the static snapshot; the
 * settlement rate applies a small deterministic drift per transaction, modelling the
 * real-world gap between what you see at swipe and what settles days later.
 */
@Service
public class FxRateService {

    private static final BigDecimal DRIFT_DENOMINATOR = new BigDecimal("100000");

    public BigDecimal midRate(Corridor corridor) {
        return corridor.usdMidRate();
    }

    public BigDecimal midRateAtSettlement(Corridor corridor, String txId) {
        int bucket = Math.floorMod(txId.hashCode(), 1001); // 0..1000
        BigDecimal drift = BigDecimal.valueOf(bucket - 500L).divide(DRIFT_DENOMINATOR); // [-0.005, +0.005]
        return corridor.usdMidRate().multiply(BigDecimal.ONE.add(drift));
    }
}
