package com.prism.analyst.adapter.tools;

import com.prism.analyst.domain.AnalystReport;
import com.prism.analyst.domain.ReportParseException;
import com.prism.transaction.TransactionService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubmitFindingsToolTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    private SubmitFindingsTool toolKnowing(Set<String> knownIds) {
        TransactionService service = new TransactionService(null, null) {
            @Override
            public boolean exists(String id) {
                return knownIds.contains(id);
            }
        };
        return new SubmitFindingsTool(service, mapper);
    }

    private JsonNode json(String raw) {
        return mapper.readTree(raw);
    }

    private static final String TWO_FINDINGS = """
            {
              "headline": "You lost $42 to hidden FX",
              "totalAddressable": "42.00",
              "findings": [
                {"title":"BRL markup","severity":"high","dollarsAtStake":"24.00","corridor":"BRL",
                 "evidenceTxIds":["real1","real2"],"recommendation":"route via stablecoin"},
                {"title":"MXN markup","severity":"medium","dollarsAtStake":"18.00","corridor":"MXN",
                 "evidenceTxIds":["real3"],"recommendation":"pay in local currency"}
              ]
            }
            """;

    @Test
    void parsesReportFromArgs() {
        AnalystReport report = toolKnowing(Set.of("real1", "real2", "real3")).parse(json(TWO_FINDINGS));
        assertEquals(2, report.findings().size());
        assertEquals("You lost $42 to hidden FX", report.headline());
    }

    @Test
    void dropsUnknownEvidenceTxIds() {
        String oneRealOneGhost = """
                {"headline":"h","totalAddressable":"10.00","findings":[
                  {"title":"t","severity":"high","dollarsAtStake":"10.00","corridor":"BRL",
                   "evidenceTxIds":["real1","ghost-1"],"recommendation":"r"}]}
                """;
        AnalystReport report = toolKnowing(Set.of("real1")).parse(json(oneRealOneGhost));
        assertEquals(java.util.List.of("real1"), report.findings().get(0).evidenceTxIds());
    }

    @Test
    void malformedArgsThrowReportParseException() {
        assertThrows(ReportParseException.class, () -> toolKnowing(Set.of()).parse(json("\"garbage\"")));
    }

    @Test
    void missingFindingsThrowReportParseException() {
        assertThrows(ReportParseException.class, () -> toolKnowing(Set.of()).parse(json("{\"headline\":\"h\"}")));
    }

    @Test
    void schemaMarksFindingsRequired() {
        JsonNode schema = toolKnowing(Set.of()).schema();
        assertTrue(schema.toString().contains("findings"));
        boolean findingsRequired = false;
        for (JsonNode r : schema.get("required")) {
            if (r.asString().equals("findings")) {
                findingsRequired = true;
            }
        }
        assertTrue(findingsRequired);
    }
}
