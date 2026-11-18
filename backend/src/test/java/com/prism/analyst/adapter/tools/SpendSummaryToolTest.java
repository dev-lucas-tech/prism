package com.prism.analyst.adapter.tools;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.prism.fx.Corridor;
import com.prism.transaction.CorridorBreakdownDto;
import com.prism.transaction.SummaryDto;
import com.prism.transaction.TransactionService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpendSummaryToolTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    private SpendSummaryTool toolReturning(SummaryDto fixed) {
        TransactionService service = new TransactionService(null, null) {
            @Override
            public SummaryDto summary() {
                return fixed;
            }
        };
        return new SpendSummaryTool(service, mapper);
    }

    private static SummaryDto fixedSummary() {
        return new SummaryDto(
                new BigDecimal("1000.00"),
                new BigDecimal("40.00"),
                250,
                new BigDecimal("15.00"),
                List.of(
                        new CorridorBreakdownDto(Corridor.BRL, 9, new BigDecimal("600.00"),
                                new BigDecimal("24.00"), new BigDecimal("9.00")),
                        new CorridorBreakdownDto(Corridor.MXN, 9, new BigDecimal("400.00"),
                                new BigDecimal("16.00"), new BigDecimal("6.00"))));
    }

    @Test
    void nameIsGetSpendSummary() {
        assertEquals("get_spend_summary", toolReturning(fixedSummary()).name());
    }

    @Test
    void returnsTotalsAndPerCorridorRows() {
        JsonNode out = toolReturning(fixedSummary()).invoke(mapper.createObjectNode());

        assertEquals(0, new BigDecimal(out.get("totalHiddenFxCost").asString()).compareTo(new BigDecimal("40.00")));
        assertEquals(250, out.get("effectiveMarkupBps").asInt());
        assertTrue(out.get("corridors").isArray());
        assertEquals(2, out.get("corridors").size());
        assertEquals("BRL", out.get("corridors").get(0).get("corridor").asString());
        assertEquals(9, out.get("corridors").get(0).get("count").asLong());
    }

    @Test
    void schemaIsObjectWithNoRequiredArgs() {
        JsonNode schema = toolReturning(fixedSummary()).schema();
        assertEquals("object", schema.get("type").asString());
        assertEquals(0, schema.get("required").size());
    }
}
