package com.prism.analyst.domain;

public interface LlmClient {

    ChatResponse complete(ChatRequest request);
}
