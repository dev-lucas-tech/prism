package com.prism.live;

import com.prism.api.SimulateRequest;
import com.prism.fx.FxRateService;
import com.prism.transaction.Transaction;
import com.prism.transaction.TransactionDto;
import com.prism.transaction.TransactionRepository;
import com.prism.transaction.TransactionService;
import com.prism.transaction.TransactionStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Drives injected transactions through their lifecycle (authorized -> settling -> settled),
 * computing the true settlement FX only at the final step and broadcasting each change.
 * This is what makes the "estimate now, real cost later" gap visible on the dashboard.
 */
@Service
public class SettlementSimulator {

    private final TransactionRepository repository;
    private final TransactionService txService;
    private final FxRateService rates;
    private final SseService sse;
    private final Queue<String> inFlight = new ConcurrentLinkedQueue<>();

    public SettlementSimulator(TransactionRepository repository, TransactionService txService,
                               FxRateService rates, SseService sse) {
        this.repository = repository;
        this.txService = txService;
        this.rates = rates;
        this.sse = sse;
    }

    public TransactionDto createLiveTransaction(SimulateRequest req) {
        String id = "live-" + UUID.randomUUID();
        Transaction t = new Transaction(
                id, "Live Demo", req.merchant(), req.corridor().country(), req.corridor(),
                req.amount(), "USD", TransactionStatus.AUTHORIZED, Instant.now(), null,
                rates.midRate(req.corridor()), null);
        repository.save(t);
        inFlight.add(id);
        TransactionDto dto = txService.toDto(t);
        sse.broadcast("tx.created", dto);
        return dto;
    }

    public void advance(String txId) {
        Transaction t = repository.findById(txId).orElse(null);
        if (t == null) {
            return;
        }
        switch (t.getStatus()) {
            case AUTHORIZED -> t.setStatus(TransactionStatus.SETTLING);
            case SETTLING -> {
                t.setStatus(TransactionStatus.SETTLED);
                t.setSettledAt(Instant.now());
                t.setMidSettleRate(rates.midRateAtSettlement(t.getCorridor(), t.getId()));
            }
            default -> {
                return;
            }
        }
        repository.save(t);
        sse.broadcast("tx.updated", txService.toDto(t));
    }

    @Scheduled(fixedDelay = 2000)
    public void tick() {
        for (String id : inFlight) {
            advance(id);
            repository.findById(id)
                    .filter(t -> t.getStatus() == TransactionStatus.SETTLED)
                    .ifPresent(t -> inFlight.remove(id));
        }
    }
}
