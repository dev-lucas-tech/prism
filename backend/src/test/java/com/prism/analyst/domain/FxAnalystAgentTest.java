package com.prism.analyst.domain;

import com.prism.analyst.adapter.tools.SubmitFindingsTool;
import com.prism.transaction.TransactionService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxAnalystAgentTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    private static final String VALID_SUBMIT = """
            {"headline":"You lost $42","totalAddressable":"42.00","findings":[
              {"title":"t","severity":"high","dollarsAtStake":"42.00","corridor":"BRL",
               "evidenceTxIds":[],"recommendation":"r"}]}
            """;
    private static final String MALFORMED_SUBMIT = "{\"headline\":\"h\"}";

    private LlmToolCall call(String id, String name, String argsJson) {
        return new LlmToolCall(id, name, mapper.readTree(argsJson));
    }

    private ChatResponse responseWith(LlmToolCall... calls) {
        return new ChatResponse(null, List.of(calls));
    }

    private static final class ScriptedLlm implements LlmClient {
        private final Deque<ChatResponse> script;

        ScriptedLlm(ChatResponse... responses) {
            this.script = new ArrayDeque<>(List.of(responses));
        }

        @Override
        public ChatResponse complete(ChatRequest request) {
            return script.isEmpty() ? new ChatResponse("", List.of()) : script.poll();
        }
    }

    private static final class CapturingSink implements AgentTraceSink {
        final List<AgentEvent> events = new ArrayList<>();

        @Override
        public void emit(AgentEvent event) {
            events.add(event);
        }

        long roundCount() {
            return events.stream().filter(e -> e instanceof AgentEvent.RoundStart).count();
        }

        boolean hasFinal() {
            return events.stream().anyMatch(e -> e instanceof AgentEvent.Final);
        }

        boolean hasError() {
            return events.stream().anyMatch(e -> e instanceof AgentEvent.Error);
        }

        boolean hasToolCall() {
            return events.stream().anyMatch(e -> e instanceof AgentEvent.ToolCall);
        }

        List<String> toolResults() {
            return events.stream()
                    .filter(e -> e instanceof AgentEvent.ToolResult)
                    .map(e -> ((AgentEvent.ToolResult) e).result().toString())
                    .toList();
        }
    }

    private AgentTool stub(String name) {
        return new AgentTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public JsonNode schema() {
                return mapper.createObjectNode().put("type", "object").put("description", "d");
            }

            @Override
            public JsonNode invoke(JsonNode args) {
                return mapper.createObjectNode().put("tool", name);
            }
        };
    }

    private SubmitFindingsTool submitTool() {
        TransactionService service = new TransactionService(null, null) {
            @Override
            public boolean exists(String id) {
                return false;
            }
        };
        return new SubmitFindingsTool(service, mapper);
    }

    private FxAnalystAgent agent(LlmClient llm, int maxRounds) {
        AgentToolRegistry registry = new AgentToolRegistry(List.of(
                stub("get_spend_summary"), stub("get_transaction_breakdown")));
        return new FxAnalystAgent(llm, registry, submitTool(), maxRounds, mapper);
    }

    @Test
    void happyPathDispatchesToolsThenReturnsReport() {
        LlmClient llm = new ScriptedLlm(
                responseWith(call("1", "get_spend_summary", "{}")),
                responseWith(call("2", "get_transaction_breakdown", "{\"id\":\"x\"}")),
                responseWith(call("3", "submit_findings", VALID_SUBMIT)));
        CapturingSink sink = new CapturingSink();

        AnalystReport report = agent(llm, 6).run(null, sink);

        assertEquals("You lost $42", report.headline());
        assertTrue(sink.hasToolCall());
        assertTrue(sink.hasFinal());
    }

    @Test
    void stopsAtMaxRoundsWithForcedFinal() {
        LlmClient llm = new ScriptedLlm(
                responseWith(call("1", "get_spend_summary", "{}")),
                responseWith(call("2", "get_spend_summary", "{}")),
                responseWith(call("3", "get_spend_summary", "{}")),
                responseWith(call("4", "submit_findings", VALID_SUBMIT)));
        CapturingSink sink = new CapturingSink();

        AnalystReport report = agent(llm, 3).run(null, sink);

        assertNotNull(report);
        assertTrue(sink.roundCount() <= 3, () -> "roundCount was " + sink.roundCount());
    }

    @Test
    void unknownToolEmitsRecoverableErrorResult() {
        LlmClient llm = new ScriptedLlm(
                responseWith(call("1", "do_evil", "{}")),
                responseWith(call("2", "submit_findings", VALID_SUBMIT)));
        CapturingSink sink = new CapturingSink();

        AnalystReport report = agent(llm, 6).run(null, sink);

        assertNotNull(report);
        assertTrue(sink.toolResults().stream().anyMatch(s -> s.contains("unknown tool")));
    }

    @Test
    void malformedSubmitEmitsErrorEventThenRecovers() {
        LlmClient llm = new ScriptedLlm(
                responseWith(call("1", "submit_findings", MALFORMED_SUBMIT)),
                responseWith(call("2", "submit_findings", VALID_SUBMIT)));
        CapturingSink sink = new CapturingSink();

        AnalystReport report = agent(llm, 6).run(null, sink);

        assertTrue(sink.hasError());
        assertNotNull(report);
    }
}
