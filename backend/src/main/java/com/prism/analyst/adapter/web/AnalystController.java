package com.prism.analyst.adapter.web;

import com.prism.analyst.domain.AgentEvent;
import com.prism.analyst.domain.AiNotConfiguredException;
import com.prism.analyst.domain.AiUnavailableException;
import com.prism.analyst.domain.FxAnalystAgent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.Executor;

@RestController
@RequestMapping("/api/analyst")
public class AnalystController {

    private static final long STREAM_TIMEOUT_MS = 300_000L;

    private final FxAnalystAgent agent;
    private final Executor executor;
    private final ObjectMapper mapper;

    public AnalystController(FxAnalystAgent agent, @Qualifier("analystExecutor") Executor executor, ObjectMapper mapper) {
        this.agent = agent;
        this.executor = executor;
        this.mapper = mapper;
    }

    @GetMapping("/stream")
    public SseEmitter stream(@RequestParam(name = "q", required = false) String question) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        SseTraceAdapter sink = new SseTraceAdapter(emitter, mapper);
        executor.execute(() -> {
            try {
                agent.run(question, sink);
            } catch (AiNotConfiguredException ex) {
                sink.emit(new AgentEvent.Error("AI is not configured. Set OPENROUTER_API_KEY to enable the analyst."));
            } catch (AiUnavailableException ex) {
                sink.emit(new AgentEvent.Error(ex.getMessage()));
            } catch (RuntimeException ex) {
                sink.emit(new AgentEvent.Error("The analyst hit an unexpected error."));
            } finally {
                emitter.complete();
            }
        });
        return emitter;
    }
}
