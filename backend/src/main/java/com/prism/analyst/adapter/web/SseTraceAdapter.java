package com.prism.analyst.adapter.web;

import com.prism.analyst.domain.AgentEvent;
import com.prism.analyst.domain.AgentTraceSink;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;

public class SseTraceAdapter implements AgentTraceSink {

    private final SseEmitter emitter;
    private final ObjectMapper mapper;

    public SseTraceAdapter(SseEmitter emitter, ObjectMapper mapper) {
        this.emitter = emitter;
        this.mapper = mapper;
    }

    @Override
    public void emit(AgentEvent event) {
        try {
            switch (event) {
                case AgentEvent.RoundStart e ->
                        send("round_start", mapper.createObjectNode().put("round", e.round()));
                case AgentEvent.ToolCall e -> {
                    ObjectNode node = mapper.createObjectNode();
                    node.put("tool", e.tool());
                    node.set("arguments", e.arguments());
                    send("tool_call", node);
                }
                case AgentEvent.ToolResult e -> {
                    ObjectNode node = mapper.createObjectNode();
                    node.put("tool", e.tool());
                    node.set("result", e.result());
                    send("tool_result", node);
                }
                case AgentEvent.Final e -> send("final", mapper.valueToTree(e.report()));
                case AgentEvent.Error e ->
                        send("error", mapper.createObjectNode().put("message", e.message()));
            }
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }

    private void send(String name, JsonNode data) throws IOException {
        emitter.send(SseEmitter.event().name(name).data(mapper.writeValueAsString(data)));
    }
}
