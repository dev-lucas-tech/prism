package com.prism.analyst.domain;

import tools.jackson.databind.JsonNode;

public record LlmToolCall(String id, String name, JsonNode arguments) {
}
