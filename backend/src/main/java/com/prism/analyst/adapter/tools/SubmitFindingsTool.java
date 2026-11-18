package com.prism.analyst.adapter.tools;

import com.prism.analyst.domain.AgentTool;
import com.prism.analyst.domain.AnalystReport;
import com.prism.analyst.domain.ReportParseException;
import com.prism.transaction.TransactionService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class SubmitFindingsTool implements AgentTool {

    private final TransactionService transactions;
    private final ObjectMapper mapper;

    public SubmitFindingsTool(TransactionService transactions, ObjectMapper mapper) {
        this.transactions = transactions;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "submit_findings";
    }

    @Override
    public JsonNode schema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("description",
                "Submit the final analysis. Call this exactly once when done. Every dollar figure must come from the tool results, and every evidence id must be a real transaction id you saw.");
        ObjectNode props = schema.putObject("properties");
        props.putObject("headline").put("type", "string");
        props.putObject("totalAddressable").put("type", "string");
        ObjectNode findings = props.putObject("findings");
        findings.put("type", "array");
        ObjectNode item = findings.putObject("items");
        item.put("type", "object");
        ObjectNode itemProps = item.putObject("properties");
        itemProps.putObject("title").put("type", "string");
        itemProps.putObject("severity").put("type", "string");
        itemProps.putObject("dollarsAtStake").put("type", "string");
        itemProps.putObject("corridor").put("type", "string");
        ObjectNode evidence = itemProps.putObject("evidenceTxIds");
        evidence.put("type", "array");
        evidence.putObject("items").put("type", "string");
        itemProps.putObject("recommendation").put("type", "string");
        item.putArray("required").add("title").add("severity").add("dollarsAtStake").add("corridor").add("recommendation");
        ArrayNode required = schema.putArray("required");
        required.add("headline").add("findings");
        return schema;
    }

    @Override
    public JsonNode invoke(JsonNode args) {
        return mapper.valueToTree(parse(args));
    }

    public AnalystReport parse(JsonNode args) {
        if (args == null || !args.isObject()) {
            throw new ReportParseException("submit_findings expects a JSON object");
        }
        JsonNode findingsNode = args.get("findings");
        if (findingsNode == null || !findingsNode.isArray()) {
            throw new ReportParseException("submit_findings requires a 'findings' array");
        }
        String headline = text(args, "headline");
        BigDecimal totalAddressable = money(args.get("totalAddressable"));

        List<AnalystReport.Finding> findings = new ArrayList<>();
        for (JsonNode f : findingsNode) {
            findings.add(new AnalystReport.Finding(
                    text(f, "title"),
                    text(f, "severity"),
                    money(f.get("dollarsAtStake")),
                    text(f, "corridor"),
                    verifiedEvidence(f.get("evidenceTxIds")),
                    text(f, "recommendation")));
        }
        return new AnalystReport(headline, totalAddressable, findings);
    }

    private List<String> verifiedEvidence(JsonNode ids) {
        List<String> verified = new ArrayList<>();
        if (ids != null && ids.isArray()) {
            for (JsonNode id : ids) {
                String value = id.asString();
                if (transactions.exists(value)) {
                    verified.add(value);
                }
            }
        }
        return verified;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asString();
    }

    private BigDecimal money(JsonNode node) {
        if (node == null || node.isNull()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(node.asString().trim());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }
}
