package com.prism.live;

import com.prism.api.SimulateRequest;
import com.prism.fx.Corridor;
import com.prism.fx.FxRateService;
import com.prism.fx.MarkupPolicy;
import com.prism.transaction.Transaction;
import com.prism.transaction.TransactionDto;
import com.prism.transaction.TransactionRepository;
import com.prism.transaction.TransactionService;
import com.prism.transaction.TransactionStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SettlementSimulatorTest {

    @Test
    void advance_movesThroughStatesAndComputesSettledFx() {
        TransactionRepository repo = mock(TransactionRepository.class);
        Map<String, Transaction> store = new HashMap<>();
        when(repo.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            store.put(t.getId(), t);
            return t;
        });
        when(repo.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(0))));

        FxRateService rates = new FxRateService();
        TransactionService txService = new TransactionService(repo, new MarkupPolicy());
        SseService sse = new SseService();
        SettlementSimulator sim = new SettlementSimulator(repo, txService, rates, sse);

        TransactionDto created = sim.createLiveTransaction(
                new SimulateRequest(Corridor.MXN, new BigDecimal("1000"), "Test Merchant"));
        assertEquals(TransactionStatus.AUTHORIZED, created.status());

        sim.advance(created.id()); // AUTHORIZED -> SETTLING
        sim.advance(created.id()); // SETTLING -> SETTLED

        Transaction settled = store.get(created.id());
        assertEquals(TransactionStatus.SETTLED, settled.getStatus());

        TransactionDto dto = txService.toDto(settled);
        assertEquals(1, dto.settledCost().compareTo(dto.midMarketCost()), "settled must exceed mid-market");
        assertEquals(1, dto.hiddenFxCost().compareTo(BigDecimal.ZERO), "hidden FX must be positive");
    }
}
