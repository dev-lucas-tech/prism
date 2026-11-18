package com.prism.analyst.domain;

public interface AgentTraceSink {

    void emit(AgentEvent event);
}
