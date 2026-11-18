package com.prism.analyst.adapter.llm;

import com.prism.analyst.config.AnalystProperties;
import com.prism.analyst.domain.AiNotConfiguredException;
import com.prism.analyst.domain.AiUnavailableException;
import com.prism.analyst.domain.ChatMessage;
import com.prism.analyst.domain.ChatRequest;
import com.prism.analyst.domain.ChatResponse;
import com.prism.analyst.domain.LlmClient;
import com.prism.analyst.domain.LlmToolCall;
import com.prism.analyst.domain.ToolSpec;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

public class OpenRouterLlmAdapter implements LlmClient {

    private static final String REFERER = "https://prism.local";
    private static final String TITLE = "Prism FX Analyst";

    private final RestClient restClient;
    private final AnalystProperties props;
    private final ObjectMapper mapper;

    public OpenRouterLlmAdapter(RestClient.Builder builder, AnalystProperties props, ObjectMapper mapper) {
        this.restClient = builder.baseUrl(props.getBaseUrl()).build();
        this.props = props;
        this.mapper = mapper;
    }

    @Override
    public ChatResponse complete(ChatRequest request) {
        if (!props.isConfigured()) {
            throw new AiNotConfiguredException("OpenRouter API key not configured (set OPENROUTER_API_KEY)");
        }
        String payload = mapper.writeValueAsString(buildBody(request));

        int attempt = 0;
        while (true) {
            try {
                String json = restClient.post()
                        .uri("/chat/completions")
                        .header("Authorization", "Bearer " + props.getApiKey())
                        .header("HTTP-Referer", REFERER)
                        .header("X-Title", TITLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .body(String.class);
                return parse(json);
            } catch (HttpStatusCodeException ex) {
                int status = ex.getStatusCode().value();
                if (status == 429 && attempt == 0) {
                    attempt++;
                    backoff();
                    continue;
                }
                if (status == 402) {
                    throw new AiUnavailableException("OpenRouter credits exhausted (HTTP 402)");
                }
                throw new AiUnavailableException("OpenRouter returned HTTP " + status);
            } catch (ResourceAccessException ex) {
                throw new AiUnavailableException("OpenRouter request failed: " + ex.getMessage());
            }
        }
    }

    private ObjectNode buildBody(ChatRequest request) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", props.getModel());
        body.put("max_tokens", props.getMaxTokens());

        ArrayNode messages = body.putArray("messages");
        for (ChatMessage m : request.messages()) {
            ObjectNode msg = messages.addObject();
            msg.put("role", m.role());
            if (m.toolCallId() != null) {
                msg.put("tool_call_id", m.toolCallId());
            }
            if (m.content() != null) {
                msg.put("content", m.content());
            }
            if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                ArrayNode toolCalls = msg.putArray("tool_calls");
                for (LlmToolCall tc : m.toolCalls()) {
                    ObjectNode node = toolCalls.addObject();
                    node.put("id", tc.id());
                    node.put("type", "function");
                    ObjectNode fn = node.putObject("function");
                    fn.put("name", tc.name());
                    fn.put("arguments", tc.arguments() == null ? "{}" : mapper.writeValueAsString(tc.arguments()));
                }
            }
            if (!msg.has("content") && !msg.has("tool_calls")) {
                msg.put("content", "");
            }
        }

        if (request.tools() != null && !request.tools().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (ToolSpec spec : request.tools()) {
                ObjectNode tool = tools.addObject();
                tool.put("type", "function");
                ObjectNode fn = tool.putObject("function");
                fn.put("name", spec.name());
                fn.put("description", spec.description());
                fn.set("parameters", spec.parameters());
            }
        }
        return body;
    }

    private ChatResponse parse(String json) {
        JsonNode message = mapper.readTree(json).path("choices").path(0).path("message");
        JsonNode contentNode = message.path("content");
        String content = contentNode.isMissingNode() || contentNode.isNull() ? null : contentNode.asString();

        List<LlmToolCall> calls = new ArrayList<>();
        JsonNode toolCalls = message.path("tool_calls");
        if (toolCalls.isArray()) {
            for (JsonNode tc : toolCalls) {
                String id = tc.path("id").asString();
                JsonNode fn = tc.path("function");
                String name = fn.path("name").asString();
                calls.add(new LlmToolCall(id, name, parseArguments(fn.path("arguments"))));
            }
        }
        return new ChatResponse(content, calls);
    }

    private JsonNode parseArguments(JsonNode argsNode) {
        if (argsNode == null || argsNode.isMissingNode() || argsNode.isNull()) {
            return mapper.createObjectNode();
        }
        if (argsNode.isObject()) {
            return argsNode;
        }
        String raw = argsNode.asString();
        if (raw == null || raw.isBlank()) {
            return mapper.createObjectNode();
        }
        String cleaned = raw.replace("```json", "").replace("```", "").trim();
        try {
            return mapper.readTree(cleaned);
        } catch (RuntimeException ex) {
            return mapper.createObjectNode();
        }
    }

    private void backoff() {
        try {
            Thread.sleep(props.getRetryBackoffMs());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("OpenRouter retry interrupted");
        }
    }
}
