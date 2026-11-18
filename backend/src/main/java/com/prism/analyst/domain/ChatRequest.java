package com.prism.analyst.domain;

import java.util.List;

public record ChatRequest(List<ChatMessage> messages, List<ToolSpec> tools) {
}
