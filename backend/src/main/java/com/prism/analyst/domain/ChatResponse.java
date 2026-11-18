package com.prism.analyst.domain;

import java.util.List;

public record ChatResponse(String assistantContent, List<LlmToolCall> toolCalls) {

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
