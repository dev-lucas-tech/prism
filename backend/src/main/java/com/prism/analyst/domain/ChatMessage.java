package com.prism.analyst.domain;

import java.util.List;

public record ChatMessage(String role, String content, List<LlmToolCall> toolCalls, String toolCallId) {

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content, List.of(), null);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, List.of(), null);
    }

    public static ChatMessage assistant(String content, List<LlmToolCall> toolCalls) {
        return new ChatMessage("assistant", content, toolCalls == null ? List.of() : toolCalls, null);
    }

    public static ChatMessage toolResult(String toolCallId, String content) {
        return new ChatMessage("tool", content, List.of(), toolCallId);
    }
}
