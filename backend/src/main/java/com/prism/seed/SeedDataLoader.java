package com.prism.seed;

import com.prism.fx.Corridor;
import com.prism.fx.FxRateService;
import com.prism.transaction.Transaction;
import com.prism.transaction.TransactionRepository;
import com.prism.transaction.TransactionStatus;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads a deterministic set of historical SETTLED transactions across every corridor,
 * so the dashboard aggregates look real on first load.
 */
@Component
public class SeedDataLoader implements ApplicationRunner {

    private static final int PER_CORRIDOR = 9;
    private static final Instant BASE = Instant.parse("2026-11-17T12:00:00Z");
    private static final String[] EMPLOYEES = {
            "Ana Torres", "Bruno Lima", "Carla Nunez", "Diego Rojas", "Elena Souza", "Felipe Castro"
    };

    private final TransactionRepository repository;
    private final FxRateService rates;

    public SeedDataLoader(TransactionRepository repository, FxRateService rates) {
        this.repository = repository;
        this.rates = rates;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        List<Transaction> txs = new ArrayList<>();
        int seq = 0;
        for (Corridor corridor : Corridor.values()) {
            String[] merchants = merchantsFor(corridor);
            String country = corridor.country();
            for (int i = 0; i < PER_CORRIDOR; i++) {
                String id = "seed-" + corridor + "-" + i;
                Instant authorizedAt = BASE.minus(Duration.ofDays((seq % 28) + 1L))
                        .minus(Duration.ofHours(seq % 12));
                Instant settledAt = authorizedAt.plus(Duration.ofDays(2));
                txs.add(new Transaction(
                        id,
                        EMPLOYEES[seq % EMPLOYEES.length],
                        merchants[i % merchants.length],
                        country,
                        corridor,
                        amountFor(corridor, i),
                        "USD",
                        TransactionStatus.SETTLED,
                        authorizedAt,
                        settledAt,
                        rates.midRate(corridor),
                        rates.midRateAtSettlement(corridor, id)));
                seq++;
            }
        }
        repository.saveAll(txs);
    }

    private static String[] merchantsFor(Corridor c) {
        return switch (c) {
            case MXN -> new String[]{"OXXO", "Mercado Libre MX", "Aeromexico", "Cemex Store"};
            case BRL -> new String[]{"iFood", "Mercado Livre BR", "LATAM Airlines", "Magazine Luiza"};
            case COP -> new String[]{"Rappi CO", "Exito", "Avianca", "Claro Colombia"};
            case EUR -> new String[]{"Amazon EU", "SNCF", "Zalando", "Lufthansa"};
            case GBP -> new String[]{"Tesco", "British Airways", "Monzo Biz", "WeWork London"};
        };
    }

    private static BigDecimal amountFor(Corridor c, int i) {
        int step = i + 1;
        return switch (c) {
            case MXN -> BigDecimal.valueOf(1500L * step + 500);
            case BRL -> BigDecimal.valueOf(350L * step + 120);
            case COP -> BigDecimal.valueOf(450000L * step + 85000);
            case EUR -> BigDecimal.valueOf(220L * step + 60);
            case GBP -> BigDecimal.valueOf(180L * step + 45);
        };
    }
}
