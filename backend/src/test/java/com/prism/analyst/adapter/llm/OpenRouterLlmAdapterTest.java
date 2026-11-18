package com.prism.analyst.adapter.llm;

import com.prism.analyst.config.AnalystProperties;
import com.prism.analyst.domain.AiNotConfiguredException;
import com.prism.analyst.domain.AiUnavailableException;
import com.prism.analyst.domain.ChatMessage;
import com.prism.analyst.domain.ChatRequest;
import com.prism.analyst.domain.ChatResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.http.HttpMethod;

class OpenRouterLlmAdapterTest {

    private static final String BASE = "https://openrouter.test/api/v1";
    private static final String TOOLCALL_JSON = """
            {"choices":[{"message":{"role":"assistant","content":null,
              "tool_calls":[{"id":"call_1","type":"function",
                "function":{"name":"get_spend_summary","arguments":"{}"}}]}}]}
            """;
    private static final String CONTENT_JSON = """
            {"choices":[{"message":{"role":"assistant","content":"done"}}]}
            """;

    private final ObjectMapper mapper = JsonMapper.builder().build();

    private AnalystProperties props(String key) {
        AnalystProperties p = new AnalystProperties();
        p.setApiKey(key);
        p.setModel("test-model");
        p.setBaseUrl(BASE);
        p.setMaxTokens(256);
        p.setRetryBackoffMs(1);
        return p;
    }

    private ChatRequest req() {
        return new ChatRequest(List.of(ChatMessage.user("hi")), List.of());
    }

    @Test
    void mapsAssistantToolCallsFromResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/chat/completions")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(TOOLCALL_JSON, MediaType.APPLICATION_JSON));

        ChatResponse response = new OpenRouterLlmAdapter(builder, props("sk-test"), mapper).complete(req());

        assertEquals(1, response.toolCalls().size());
        assertEquals("get_spend_summary", response.toolCalls().get(0).name());
        server.verify();
    }

    @Test
    void missingApiKeyThrowsAiNotConfigured() {
        OpenRouterLlmAdapter adapter = new OpenRouterLlmAdapter(RestClient.builder(), props(null), mapper);
        assertThrows(AiNotConfiguredException.class, () -> adapter.complete(req()));
    }

    @Test
    void retriesOnceOn429ThenSucceeds() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/chat/completions")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(BASE + "/chat/completions"))
                .andRespond(withSuccess(CONTENT_JSON, MediaType.APPLICATION_JSON));

        ChatResponse response = new OpenRouterLlmAdapter(builder, props("sk-test"), mapper).complete(req());

        assertEquals("done", response.assistantContent());
        server.verify();
    }

    @Test
    void http402ThrowsAiUnavailableWithCreditsMessage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/chat/completions")).andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED));

        AiUnavailableException ex = assertThrows(AiUnavailableException.class,
                () -> new OpenRouterLlmAdapter(builder, props("sk-test"), mapper).complete(req()));
        assertTrue(ex.getMessage().toLowerCase().contains("credit"));
    }

    @Test
    void sendsBearerAuthorizationHeader() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/chat/completions"))
                .andExpect(header("Authorization", "Bearer sk-test"))
                .andRespond(withSuccess(CONTENT_JSON, MediaType.APPLICATION_JSON));

        new OpenRouterLlmAdapter(builder, props("sk-test"), mapper).complete(req());
        server.verify();
    }
}
