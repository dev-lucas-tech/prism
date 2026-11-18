package com.prism.analyst.domain;

import tools.jackson.databind.JsonNode;

public interface FindingsSubmission extends AgentTool {

    AnalystReport parse(JsonNode args);
}
