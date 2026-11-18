package com.prism.transaction;

import com.prism.fx.Corridor;
import com.prism.fx.MarkupPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionServiceTest {

    private final MarkupPolicy markup = new MarkupPolicy();

    private Transaction settled(String id, Corridor c, String amount) {
        return new Transaction(id, "Emp", "Merchant", "Country", c, new BigDecimal(amount), "USD",
                TransactionStatus.SETTLED, Instant.parse("2026-11-10T00:00:00Z"),
                Instant.parse("2026-11-12T00:00:00Z"), c.usdMidRate(), c.usdMidRate());
    }

    private Transaction authorized(String id, Corridor c, String amount) {
        return new Transaction(id, "Emp", "Merchant", "Country", c, new BigDecimal(amount), "USD",
                TransactionStatus.AUTHORIZED, Instant.parse("2026-11-16T00:00:00Z"),
                null, c.usdMidRate(), null);
    }

    @Test
    void summary_emptyRepo_returnsZeroedAggregates() {
        TransactionRepository repo = mock(TransactionRepository.class);
        when(repo.findAll()).thenReturn(List.of());
        TransactionService svc = new TransactionService(repo, markup);

        SummaryDto s = svc.summary();

        assertEquals(0, s.totalSpend().compareTo(BigDecimal.ZERO));
        assertEquals(0, s.effectiveMarkupBps());
        assertTrue(s.corridorBreakdown().isEmpty());
    }

    @Test
    void summary_excludesAuthorizedTransactions() {
        TransactionRepository repo = mock(TransactionRepository.class);
        when(repo.findAll()).thenReturn(List.of(
                settled("s1", Corridor.MXN, "1000"),
                authorized("a1", Corridor.EUR, "100")));
        TransactionService svc = new TransactionService(repo, markup);

        SummaryDto s = svc.summary();

        // only the settled MXN tx counts: 1000 * 0.054 * 1.025 = 55.35
        assertEquals(0, s.totalSpend().compareTo(new BigDecimal("55.35")));
        assertEquals(1, s.corridorBreakdown().size());
        assertEquals(Corridor.MXN, s.corridorBreakdown().get(0).corridor());
    }

    @Test
    void exists_delegatesToRepositoryAndGuardsNull() {
        TransactionRepository repo = mock(TransactionRepository.class);
        when(repo.existsById("x")).thenReturn(true);
        TransactionService svc = new TransactionService(repo, markup);

        assertTrue(svc.exists("x"));
        assertFalse(svc.exists("y"));
        assertFalse(svc.exists(null));
        verify(repo, never()).existsById(null);
    }

    @Test
    void toDto_authorized_hasEstimateButNoSettledFields() {
        TransactionRepository repo = mock(TransactionRepository.class);
        TransactionService svc = new TransactionService(repo, markup);

        TransactionDto dto = svc.toDto(authorized("a1", Corridor.MXN, "1000"));

        assertEquals(0, dto.estimatedCost().compareTo(new BigDecimal("54.00")));
        assertNull(dto.settledCost());
        assertNull(dto.hiddenFxCost());
        assertNull(dto.effectiveMarkupBps());
    }
}
