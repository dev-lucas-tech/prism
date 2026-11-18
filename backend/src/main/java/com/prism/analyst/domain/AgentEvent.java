package com.prism.analyst.domain;

import tools.jackson.databind.JsonNode;

public sealed interface AgentEvent
        permits AgentEvent.RoundStart, AgentEvent.ToolCall, AgentEvent.ToolResult, AgentEvent.Final, AgentEvent.Error {

    record RoundStart(int round) implements AgentEvent {
    }

    record ToolCall(String tool, JsonNode arguments) implements AgentEvent {
    }

    record ToolResult(String tool, JsonNode result) implements AgentEvent {
    }

    record Final(AnalystReport report) implements AgentEvent {
    }

    record Error(String message) implements AgentEvent {
    }
}
