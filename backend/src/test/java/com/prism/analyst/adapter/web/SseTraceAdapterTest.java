package com.prism.analyst.adapter.web;

import com.prism.analyst.domain.AgentEvent;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SseTraceAdapterTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void swallowsIllegalStateWhenEmitterAlreadyClosed() {
        AtomicBoolean completedWithError = new AtomicBoolean(false);
        SseEmitter emitter = new SseEmitter() {
            @Override
            public void send(SseEventBuilder builder) throws IOException {
                throw new IllegalStateException("ResponseBodyEmitter has already completed");
            }

            @Override
            public void completeWithError(Throwable ex) {
                completedWithError.set(true);
            }
        };
        SseTraceAdapter adapter = new SseTraceAdapter(emitter, mapper);

        assertDoesNotThrow(() -> adapter.emit(new AgentEvent.RoundStart(1)));
        assertTrue(completedWithError.get());
    }
}
