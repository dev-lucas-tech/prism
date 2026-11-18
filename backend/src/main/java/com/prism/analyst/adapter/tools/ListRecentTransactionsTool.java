package com.prism.analyst.adapter.tools;

import com.prism.analyst.domain.AgentTool;
import com.prism.fx.Corridor;
import com.prism.transaction.TransactionDto;
import com.prism.transaction.TransactionService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class ListRecentTransactionsTool implements AgentTool {

    private static final int MAX_LIMIT = 30;

    private final TransactionService transactions;
    private final ObjectMapper mapper;

    public ListRecentTransactionsTool(TransactionService transactions, ObjectMapper mapper) {
        this.transactions = transactions;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "list_recent_transactions";
    }

    @Override
    public JsonNode schema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("description",
                "List recent settled card transactions (most recent first), optionally narrowed by corridor or a time window. Returns at most 30 compact rows.");
        ObjectNode props = schema.putObject("properties");
        ObjectNode limit = props.putObject("limit");
        limit.put("type", "integer");
        limit.put("description", "Max rows to return, 1..30 (default 30).");
        ObjectNode corridor = props.putObject("corridor");
        corridor.put("type", "string");
        ArrayNode corridorEnum = corridor.putArray("enum");
        for (Corridor c : Corridor.values()) {
            corridorEnum.add(c.name());
        }
        corridor.put("description", "Restrict to one corridor.");
        ObjectNode sinceDays = props.putObject("sinceDays");
        sinceDays.put("type", "integer");
        sinceDays.put("description", "Only transactions authorized within the last N days.");
        schema.set("required", mapper.createArrayNode());
        schema.put("additionalProperties", false);
        return schema;
    }

    @Override
    public JsonNode invoke(JsonNode args) {
        int limit = MAX_LIMIT;
        if (args.has("limit") && args.get("limit").isNumber()) {
            limit = Math.max(1, Math.min(MAX_LIMIT, args.get("limit").asInt()));
        }

        Corridor corridorFilter = null;
        if (args.has("corridor") && !args.get("corridor").isNull()) {
            String raw = args.get("corridor").asString();
            try {
                corridorFilter = Corridor.valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                return error("unknown corridor: " + raw);
            }
        }

        Instant cutoff = null;
        if (args.has("sinceDays") && args.get("sinceDays").isNumber()) {
            int days = args.get("sinceDays").asInt();
            if (days > 0) {
                cutoff = Instant.now().minus(Duration.ofDays(days));
            }
        }

        List<TransactionDto> rows = transactions.list();
        ObjectNode out = mapper.createObjectNode();
        ArrayNode arr = out.putArray("transactions");
        int kept = 0;
        for (TransactionDto t : rows) {
            if (kept >= limit) {
                break;
            }
            if (corridorFilter != null && t.corridor() != corridorFilter) {
                continue;
            }
            if (cutoff != null && t.authorizedAt() != null && t.authorizedAt().isBefore(cutoff)) {
                continue;
            }
            ObjectNode row = arr.addObject();
            row.put("id", t.id());
            row.put("date", t.authorizedAt() == null ? null : t.authorizedAt().toString());
            row.put("merchant", t.merchant());
            row.put("corridor", t.corridor().name());
            row.put("purchaseAmount", t.purchaseAmount().toPlainString());
            row.put("settledCost", t.settledCost() == null ? null : t.settledCost().toPlainString());
            row.put("hiddenFxCost", t.hiddenFxCost() == null ? null : t.hiddenFxCost().toPlainString());
            row.put("markupBps", t.effectiveMarkupBps());
            kept++;
        }
        out.put("count", kept);
        return out;
    }

    private ObjectNode error(String message) {
        ObjectNode node = mapper.createObjectNode();
        node.put("error", message);
        return node;
    }
}
