package com.prism.transaction;

import com.prism.fx.Corridor;
import com.prism.fx.FxBreakdown;
import com.prism.fx.FxCalculator;
import com.prism.fx.MarkupPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class TransactionService {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode RM = RoundingMode.HALF_EVEN;
    private static final BigDecimal BPS = new BigDecimal("10000");

    private final TransactionRepository repository;
    private final MarkupPolicy markupPolicy;

    public TransactionService(TransactionRepository repository, MarkupPolicy markupPolicy) {
        this.repository = repository;
        this.markupPolicy = markupPolicy;
    }

    public List<TransactionDto> list() {
        return repository.findAllByOrderByAuthorizedAtDesc().stream().map(this::toDto).toList();
    }

    public TransactionDto getById(String id) {
        return repository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "transaction not found: " + id));
    }

    public boolean exists(String id) {
        return id != null && repository.existsById(id);
    }

    public TransactionDto toDto(Transaction t) {
        BigDecimal estimatedCost = t.getPurchaseAmount().multiply(t.getMidAuthRate()).setScale(MONEY_SCALE, RM);
        if (t.getMidSettleRate() == null) {
            return new TransactionDto(t.getId(), t.getEmployee(), t.getMerchant(), t.getMerchantCountry(),
                    t.getCorridor(), t.getPurchaseAmount(), t.getFundingCurrency(), t.getStatus(),
                    t.getAuthorizedAt(), t.getSettledAt(), t.getMidAuthRate(), t.getMidSettleRate(),
                    estimatedCost, null, null, null, null, null, null, null);
        }
        FxBreakdown b = breakdown(t);
        return new TransactionDto(t.getId(), t.getEmployee(), t.getMerchant(), t.getMerchantCountry(),
                t.getCorridor(), t.getPurchaseAmount(), t.getFundingCurrency(), t.getStatus(),
                t.getAuthorizedAt(), t.getSettledAt(), t.getMidAuthRate(), t.getMidSettleRate(),
                b.estimatedCost(), b.midMarketCost(), b.settledCost(), b.hiddenFxCost(),
                b.effectiveMarkupBps(), b.stablecoinCost(), b.stablecoinSavings(), b.surpriseVsEstimate());
    }

    public SummaryDto summary() {
        List<Transaction> settled = repository.findAll().stream()
                .filter(t -> t.getMidSettleRate() != null && t.getStatus() == TransactionStatus.SETTLED)
                .toList();

        BigDecimal totalSpend = BigDecimal.ZERO;
        BigDecimal totalHidden = BigDecimal.ZERO;
        BigDecimal totalMidMarket = BigDecimal.ZERO;
        BigDecimal totalSavings = BigDecimal.ZERO;

        Map<Corridor, long[]> counts = new EnumMap<>(Corridor.class);
        Map<Corridor, BigDecimal[]> sums = new EnumMap<>(Corridor.class); // [spend, hidden, savings]

        for (Transaction t : settled) {
            FxBreakdown b = breakdown(t);
            totalSpend = totalSpend.add(b.settledCost());
            totalHidden = totalHidden.add(b.hiddenFxCost());
            totalMidMarket = totalMidMarket.add(b.midMarketCost());
            totalSavings = totalSavings.add(b.stablecoinSavings());

            Corridor c = t.getCorridor();
            counts.computeIfAbsent(c, k -> new long[]{0})[0]++;
            BigDecimal[] agg = sums.computeIfAbsent(c,
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            agg[0] = agg[0].add(b.settledCost());
            agg[1] = agg[1].add(b.hiddenFxCost());
            agg[2] = agg[2].add(b.stablecoinSavings());
        }

        int effectiveMarkupBps = totalMidMarket.signum() == 0 ? 0
                : totalHidden.divide(totalMidMarket, 10, RM).multiply(BPS).setScale(0, RM).intValueExact();

        List<CorridorBreakdownDto> breakdown = new ArrayList<>();
        for (Corridor c : Corridor.values()) {
            if (sums.containsKey(c)) {
                BigDecimal[] agg = sums.get(c);
                breakdown.add(new CorridorBreakdownDto(c, counts.get(c)[0],
                        agg[0].setScale(MONEY_SCALE, RM), agg[1].setScale(MONEY_SCALE, RM),
                        agg[2].setScale(MONEY_SCALE, RM)));
            }
        }

        return new SummaryDto(
                totalSpend.setScale(MONEY_SCALE, RM),
                totalHidden.setScale(MONEY_SCALE, RM),
                effectiveMarkupBps,
                totalSavings.setScale(MONEY_SCALE, RM),
                breakdown);
    }

    private FxBreakdown breakdown(Transaction t) {
        return FxCalculator.compute(t.getPurchaseAmount(), t.getMidAuthRate(), t.getMidSettleRate(),
                markupPolicy.markupBps(t.getCorridor()), markupPolicy.stablecoinFeeBps());
    }
}
