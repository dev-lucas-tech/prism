package com.prism.analyst.adapter.tools;

import com.prism.analyst.domain.AgentTool;
import com.prism.transaction.TransactionDto;
import com.prism.transaction.TransactionService;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

public class GetTransactionBreakdownTool implements AgentTool {

    private final TransactionService transactions;
    private final ObjectMapper mapper;

    public GetTransactionBreakdownTool(TransactionService transactions, ObjectMapper mapper) {
        this.transactions = transactions;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "get_transaction_breakdown";
    }

    @Override
    public JsonNode schema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("description",
                "Full FX decomposition for one transaction by id: settled cost, mid-market cost, hidden FX cost, effective markup, and the stablecoin-corridor alternative.");
        ObjectNode props = schema.putObject("properties");
        ObjectNode id = props.putObject("id");
        id.put("type", "string");
        id.put("description", "The transaction id, as returned by list_recent_transactions.");
        schema.putArray("required").add("id");
        schema.put("additionalProperties", false);
        return schema;
    }

    @Override
    public JsonNode invoke(JsonNode args) {
        if (!args.has("id") || args.get("id").isNull() || args.get("id").asString().isBlank()) {
            return error("missing required argument: id");
        }
        String id = args.get("id").asString();
        TransactionDto t;
        try {
            t = transactions.getById(id);
        } catch (ResponseStatusException ex) {
            return error("transaction not found: " + id);
        }
        ObjectNode out = mapper.createObjectNode();
        out.put("id", t.id());
        out.put("merchant", t.merchant());
        out.put("corridor", t.corridor().name());
        out.put("purchaseAmount", t.purchaseAmount().toPlainString());
        out.put("status", t.status().name());
        putMoney(out, "estimatedCost", t.estimatedCost());
        putMoney(out, "midMarketCost", t.midMarketCost());
        putMoney(out, "settledCost", t.settledCost());
        putMoney(out, "hiddenFxCost", t.hiddenFxCost());
        out.put("effectiveMarkupBps", t.effectiveMarkupBps());
        putMoney(out, "stablecoinCost", t.stablecoinCost());
        putMoney(out, "stablecoinSavings", t.stablecoinSavings());
        putMoney(out, "surpriseVsEstimate", t.surpriseVsEstimate());
        return out;
    }

    private void putMoney(ObjectNode node, String field, java.math.BigDecimal value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value.toPlainString());
        }
    }

    private ObjectNode error(String message) {
        ObjectNode node = mapper.createObjectNode();
        node.put("error", message);
        return node;
    }
}
