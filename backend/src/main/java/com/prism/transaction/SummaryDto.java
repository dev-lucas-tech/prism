package com.prism.transaction;

import java.math.BigDecimal;
import java.util.List;

public record SummaryDto(
        BigDecimal totalSpend,
        BigDecimal totalHiddenFxCost,
        int effectiveMarkupBps,
        BigDecimal totalStablecoinSavings,
        List<CorridorBreakdownDto> corridorBreakdown
) {
}
