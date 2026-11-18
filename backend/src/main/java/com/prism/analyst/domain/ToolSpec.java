package com.prism.analyst.domain;

import tools.jackson.databind.JsonNode;

public record ToolSpec(String name, String description, JsonNode parameters) {
}
