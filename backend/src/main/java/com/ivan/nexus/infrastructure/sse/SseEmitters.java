package com.ivan.nexus.infrastructure.sse;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

public final class SseEmitters {
    private SseEmitters() {
    }

    public static boolean send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        synchronized (emitter) {
            try {
                emitter.send(event);
                return true;
            } catch (IOException | IllegalStateException ex) {
                complete(emitter);
                return false;
            }
        }
    }

    public static void complete(SseEmitter emitter) {
        synchronized (emitter) {
            try {
                emitter.complete();
            } catch (IllegalStateException ignored) {
                // Client already closed the stream.
            }
        }
    }

    public static void completeWithError(SseEmitter emitter, Exception ex) {
        synchronized (emitter) {
            try {
                emitter.completeWithError(ex);
            } catch (IllegalStateException ignored) {
                // Client already closed the stream.
            }
        }
    }
}
