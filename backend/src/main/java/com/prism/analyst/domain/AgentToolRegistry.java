package com.prism.analyst.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AgentToolRegistry {

    private final Map<String, AgentTool> byName = new LinkedHashMap<>();

    public AgentToolRegistry(List<AgentTool> tools) {
        for (AgentTool tool : tools) {
            byName.put(tool.name(), tool);
        }
    }

    public Optional<AgentTool> find(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public List<AgentTool> all() {
        return List.copyOf(byName.values());
    }
}
