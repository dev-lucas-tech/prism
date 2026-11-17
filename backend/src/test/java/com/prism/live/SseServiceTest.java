package com.prism.live;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SseServiceTest {

    @Test
    void broadcast_afterEmitterCompletes_doesNotThrow() {
        SseService svc = new SseService();
        var emitter = svc.subscribe();
        assertEquals(1, svc.activeCount());

        emitter.complete();

        assertDoesNotThrow(() -> svc.broadcast("tx.updated", Map.of("k", "v")));
        assertEquals(0, svc.activeCount(), "a dead emitter must be pruned");
    }
}
