package com.prism.analyst.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("prism.ai")
public class AnalystProperties {

    private String apiKey = "";
    private String model = "anthropic/claude-sonnet-4.5";
    private String baseUrl = "https://openrouter.ai/api/v1";
    private int maxTokens = 1024;
    private int maxRounds = 6;
    private long connectTimeoutMs = 10_000;
    private long readTimeoutMs = 60_000;
    private long retryBackoffMs = 600;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getMaxRounds() {
        return maxRounds;
    }

    public void setMaxRounds(int maxRounds) {
        this.maxRounds = maxRounds;
    }

    public long getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(long connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public long getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(long readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public long getRetryBackoffMs() {
        return retryBackoffMs;
    }

    public void setRetryBackoffMs(long retryBackoffMs) {
        this.retryBackoffMs = retryBackoffMs;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
