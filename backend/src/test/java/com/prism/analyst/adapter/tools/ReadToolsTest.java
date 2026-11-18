package com.prism.analyst.adapter.tools;

import com.prism.fx.Corridor;
import com.prism.transaction.TransactionDto;
import com.prism.transaction.TransactionService;
import com.prism.transaction.TransactionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadToolsTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    private TransactionDto dto(String id, Corridor c, Instant authAt, String settledCost, String hiddenFx, int markupBps) {
        return new TransactionDto(id, "Employee", "Merchant-" + id, c.country(), c,
                new BigDecimal("100"), "USD", TransactionStatus.SETTLED, authAt, authAt.plus(Duration.ofDays(2)),
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal(settledCost), new BigDecimal(hiddenFx), markupBps,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private TransactionService serviceWith(List<TransactionDto> rows) {
        return new TransactionService(null, null) {
            @Override
            public List<TransactionDto> list() {
                return rows;
            }

            @Override
            public TransactionDto getById(String id) {
                return rows.stream().filter(t -> t.id().equals(id)).findFirst()
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "transaction not found: " + id));
            }
        };
    }

    private ObjectNode args() {
        return mapper.createObjectNode();
    }

    private List<TransactionDto> thirtyFiveRecent() {
        Instant now = Instant.now();
        return java.util.stream.IntStream.range(0, 35)
                .mapToObj(i -> dto("t" + i, Corridor.BRL, now.minus(Duration.ofDays(1)), "100.00", "3.00", 300))
                .toList();
    }

    @Test
    void listClampsLimitToThirty() {
        ListRecentTransactionsTool tool = new ListRecentTransactionsTool(serviceWith(thirtyFiveRecent()), mapper);
        JsonNode out = tool.invoke(args().put("limit", 999));
        assertEquals(30, out.get("transactions").size());
    }

    @Test
    void listDefaultsToThirtyWhenNoLimit() {
        ListRecentTransactionsTool tool = new ListRecentTransactionsTool(serviceWith(thirtyFiveRecent()), mapper);
        assertEquals(30, tool.invoke(args()).get("transactions").size());
    }

    @Test
    void listRejectsUnknownCorridorWithToolError() {
        ListRecentTransactionsTool tool = new ListRecentTransactionsTool(serviceWith(thirtyFiveRecent()), mapper);
        JsonNode out = tool.invoke(args().put("corridor", "XYZ"));
        assertTrue(out.has("error"));
        assertFalse(out.has("transactions"));
    }

    @Test
    void listFiltersByCorridor() {
        Instant now = Instant.now();
        TransactionService svc = serviceWith(List.of(
                dto("a", Corridor.BRL, now.minus(Duration.ofDays(1)), "100.00", "3.00", 300),
                dto("b", Corridor.MXN, now.minus(Duration.ofDays(1)), "100.00", "2.50", 250)));
        JsonNode out = new ListRecentTransactionsTool(svc, mapper).invoke(args().put("corridor", "BRL"));
        assertEquals(1, out.get("transactions").size());
        assertEquals("a", out.get("transactions").get(0).get("id").asString());
    }

    @Test
    void listFiltersBySinceDays() {
        Instant now = Instant.now();
        TransactionService svc = serviceWith(List.of(
                dto("recent", Corridor.BRL, now.minus(Duration.ofDays(2)), "100.00", "3.00", 300),
                dto("old", Corridor.BRL, now.minus(Duration.ofDays(40)), "100.00", "3.00", 300)));
        JsonNode out = new ListRecentTransactionsTool(svc, mapper).invoke(args().put("sinceDays", 15));
        assertEquals(1, out.get("transactions").size());
        assertEquals("recent", out.get("transactions").get(0).get("id").asString());
    }

    @Test
    void breakdownReturnsHiddenFxForKnownId() {
        Instant now = Instant.now();
        TransactionService svc = serviceWith(List.of(dto("known", Corridor.BRL, now, "342.99", "9.99", 300)));
        JsonNode out = new GetTransactionBreakdownTool(svc, mapper).invoke(args().put("id", "known"));
        assertEquals(0, new BigDecimal(out.get("hiddenFxCost").asString()).compareTo(new BigDecimal("9.99")));
    }

    @Test
    void breakdownUnknownIdReturnsToolErrorNotException() {
        TransactionService svc = serviceWith(List.of());
        JsonNode out = new GetTransactionBreakdownTool(svc, mapper).invoke(args().put("id", "nope"));
        assertTrue(out.has("error"));
    }
}
