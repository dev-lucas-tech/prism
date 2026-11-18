package com.prism.fx;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class EstimateService {

    private final FxRateService rates;
    private final MarkupPolicy markup;

    public EstimateService(FxRateService rates, MarkupPolicy markup) {
        this.rates = rates;
        this.markup = markup;
    }

    public EstimateDto estimate(Corridor corridor, BigDecimal amount) {
        BigDecimal mid = rates.midRate(corridor);
        int markupBps = markup.markupBps(corridor);
        int stablecoinFeeBps = markup.stablecoinFeeBps();

        FxBreakdown b = FxCalculator.compute(amount, mid, mid, markupBps, stablecoinFeeBps);

        return new EstimateDto(
                corridor.name(),
                corridor.country(),
                amount.setScale(2, RoundingMode.HALF_EVEN),
                mid,
                b.midMarketCost(),
                b.settledCost(),
                b.hiddenFxCost(),
                b.effectiveMarkupBps(),
                stablecoinFeeBps,
                b.stablecoinCost(),
                b.stablecoinSavings()
        );
    }
}
