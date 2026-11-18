package com.prism.analyst.domain;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

public class FxAnalystAgent {

    private static final String SYSTEM_PROMPT = """
            You are an FX savings analyst for a Jeeves corporate-card customer. Investigate the customer's \
            recent settled card transactions using the available tools, then call submit_findings exactly once.
            Rules:
            - Every dollar figure in your findings MUST come from a tool result. Never invent or estimate numbers.
            - Start with get_spend_summary, then drill into the worst corridors with list_recent_transactions \
            and get_transaction_breakdown before concluding.
            - Each finding must cite the real transaction ids (evidenceTxIds) it is based on.
            - Be concise and specific. Prioritise the largest, most addressable FX leakage.""";

    private static final String DEFAULT_QUESTION =
            "Analyze my last 30 card purchases and tell me where I'm losing the most to FX and what to do about it.";

    private final LlmClient llm;
    private final AgentToolRegistry registry;
    private final FindingsSubmission submitTool;
    private final int maxRounds;
    private final ObjectMapper mapper;

    public FxAnalystAgent(LlmClient llm, AgentToolRegistry registry, FindingsSubmission submitTool,
                          int maxRounds, ObjectMapper mapper) {
        this.llm = llm;
        this.registry = registry;
        this.submitTool = submitTool;
        this.maxRounds = maxRounds;
        this.mapper = mapper;
    }

    public AnalystReport run(String question, AgentTraceSink sink) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(SYSTEM_PROMPT));
        messages.add(ChatMessage.user(question == null || question.isBlank() ? DEFAULT_QUESTION : question));
        List<ToolSpec> specs = buildSpecs();

        for (int round = 1; round <= maxRounds; round++) {
            sink.emit(new AgentEvent.RoundStart(round));
            ChatResponse response = llm.complete(new ChatRequest(messages, specs));
            if (!response.hasToolCalls()) {
                messages.add(ChatMessage.assistant(response.assistantContent(), List.of()));
                messages.add(ChatMessage.user("Call submit_findings now to finish."));
                continue;
            }
            messages.add(ChatMessage.assistant(response.assistantContent(), response.toolCalls()));
            AnalystReport report = handleToolCalls(response.toolCalls(), messages, sink);
            if (report != null) {
                return report;
            }
        }
        return forcedFinal(messages, specs, sink);
    }

    private AnalystReport handleToolCalls(List<LlmToolCall> calls, List<ChatMessage> messages, AgentTraceSink sink) {
        for (LlmToolCall call : calls) {
            sink.emit(new AgentEvent.ToolCall(call.name(), call.arguments()));
            if (submitTool.name().equals(call.name())) {
                AnalystReport report = trySubmit(call, messages, sink);
                if (report != null) {
                    return report;
                }
                continue;
            }
            JsonNode result = dispatch(call);
            sink.emit(new AgentEvent.ToolResult(call.name(), result));
            messages.add(ChatMessage.toolResult(call.id(), mapper.writeValueAsString(result)));
        }
        return null;
    }

    private AnalystReport trySubmit(LlmToolCall call, List<ChatMessage> messages, AgentTraceSink sink) {
        try {
            AnalystReport report = submitTool.parse(call.arguments());
            sink.emit(new AgentEvent.Final(report));
            return report;
        } catch (ReportParseException ex) {
            sink.emit(new AgentEvent.Error("submit_findings was malformed: " + ex.getMessage()));
            messages.add(ChatMessage.toolResult(call.id(),
                    "{\"error\":\"malformed submission, fix the schema and call submit_findings again\"}"));
            return null;
        }
    }

    private JsonNode dispatch(LlmToolCall call) {
        return registry.find(call.name())
                .map(tool -> tool.invoke(call.arguments()))
                .orElseGet(() -> errorNode("unknown tool: " + call.name()));
    }

    private AnalystReport forcedFinal(List<ChatMessage> messages, List<ToolSpec> specs, AgentTraceSink sink) {
        messages.add(ChatMessage.user(
                "You have gathered enough information. Call submit_findings now with your conclusions, using only figures from the tool results."));
        ChatResponse response = llm.complete(new ChatRequest(messages, specs));
        if (response.hasToolCalls()) {
            for (LlmToolCall call : response.toolCalls()) {
                if (submitTool.name().equals(call.name())) {
                    sink.emit(new AgentEvent.ToolCall(call.name(), call.arguments()));
                    try {
                        AnalystReport report = submitTool.parse(call.arguments());
                        sink.emit(new AgentEvent.Final(report));
                        return report;
                    } catch (ReportParseException ex) {
                        sink.emit(new AgentEvent.Error("final submission malformed: " + ex.getMessage()));
                    }
                }
            }
        }
        sink.emit(new AgentEvent.Error("agent did not produce a report within the round limit"));
        return null;
    }

    private List<ToolSpec> buildSpecs() {
        List<ToolSpec> specs = new ArrayList<>();
        for (AgentTool tool : registry.all()) {
            specs.add(toSpec(tool));
        }
        specs.add(toSpec(submitTool));
        return specs;
    }

    private ToolSpec toSpec(AgentTool tool) {
        JsonNode schema = tool.schema();
        String description = schema.path("description").isMissingNode() ? "" : schema.path("description").asString();
        return new ToolSpec(tool.name(), description, schema);
    }

    private ObjectNode errorNode(String message) {
        ObjectNode node = mapper.createObjectNode();
        node.put("error", message);
        return node;
    }
}
