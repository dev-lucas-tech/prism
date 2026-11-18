package com.prism.analyst.adapter.tools;

import com.prism.analyst.domain.AgentTool;
import com.prism.transaction.CorridorBreakdownDto;
import com.prism.transaction.SummaryDto;
import com.prism.transaction.TransactionService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public class SpendSummaryTool implements AgentTool {

    private final TransactionService transactions;
    private final ObjectMapper mapper;

    public SpendSummaryTool(TransactionService transactions, ObjectMapper mapper) {
        this.transactions = transactions;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "get_spend_summary";
    }

    @Override
    public JsonNode schema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("description",
                "Aggregate FX spend across all recent settled card transactions: totals plus a per-corridor breakdown. Start here.");
        schema.set("properties", mapper.createObjectNode());
        schema.set("required", mapper.createArrayNode());
        schema.put("additionalProperties", false);
        return schema;
    }

    @Override
    public JsonNode invoke(JsonNode args) {
        SummaryDto summary = transactions.summary();
        ObjectNode out = mapper.createObjectNode();
        out.put("totalSpend", summary.totalSpend().toPlainString());
        out.put("totalHiddenFxCost", summary.totalHiddenFxCost().toPlainString());
        out.put("effectiveMarkupBps", summary.effectiveMarkupBps());
        out.put("totalStablecoinSavings", summary.totalStablecoinSavings().toPlainString());
        ArrayNode corridors = out.putArray("corridors");
        for (CorridorBreakdownDto row : summary.corridorBreakdown()) {
            ObjectNode node = corridors.addObject();
            node.put("corridor", row.corridor().name());
            node.put("count", row.count());
            node.put("spend", row.spend().toPlainString());
            node.put("hiddenFxCost", row.hiddenFxCost().toPlainString());
            node.put("savings", row.stablecoinSavings().toPlainString());
        }
        return out;
    }
}
