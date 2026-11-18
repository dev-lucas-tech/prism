package com.prism.analyst.domain;

import tools.jackson.databind.JsonNode;

public interface AgentTool {

    String name();

    JsonNode schema();

    JsonNode invoke(JsonNode args);
}
