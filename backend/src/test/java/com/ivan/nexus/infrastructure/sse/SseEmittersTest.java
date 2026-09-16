package com.ivan.nexus.infrastructure.sse;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThatCode;

class SseEmittersTest {
    @Test
    void sendAfterCompleteDoesNotThrow() {
        SseEmitter emitter = new SseEmitter(0L);
        SseEmitters.complete(emitter);

        assertThatCode(() -> SseEmitters.send(emitter, SseEmitter.event().name("log").data("late line")))
                .doesNotThrowAnyException();
    }

    @Test
    void completeTwiceDoesNotThrow() {
        SseEmitter emitter = new SseEmitter(0L);

        assertThatCode(() -> {
            SseEmitters.complete(emitter);
            SseEmitters.complete(emitter);
        }).doesNotThrowAnyException();
    }
}
