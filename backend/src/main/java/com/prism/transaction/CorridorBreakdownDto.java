package com.prism.transaction;

import com.prism.fx.Corridor;

import java.math.BigDecimal;

public record CorridorBreakdownDto(
        Corridor corridor,
        long count,
        BigDecimal spend,
        BigDecimal hiddenFxCost,
        BigDecimal stablecoinSavings
) {
}
