package com.ivan.nexus.infrastructure.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivan.nexus.application.activity.ActivityPublisher;
import com.ivan.nexus.domain.activity.Activity;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class ActivityHub implements ActivityPublisher {
    private static final Logger log = LoggerFactory.getLogger(ActivityHub.class);
    private static final long HEARTBEAT_SECONDS = 15;

    private final ConcurrentHashMap<SseEmitter, Boolean> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService heartbeat;

    public ActivityHub(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.heartbeat = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "activity-sse-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        this.heartbeat.scheduleAtFixedRate(
                this::pingAll,
                HEARTBEAT_SECONDS,
                HEARTBEAT_SECONDS,
                TimeUnit.SECONDS);
    }

    public void subscribe(SseEmitter emitter) {
        emitters.put(emitter, Boolean.TRUE);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));
    }

    @Override
    public void publish(Activity event) {
        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize activity event {}", event.id(), ex);
            return;
        }
        for (SseEmitter emitter : emitters.keySet()) {
            sendActivity(emitter, json);
        }
    }

    private void pingAll() {
        for (SseEmitter emitter : emitters.keySet()) {
            sendHeartbeat(emitter);
        }
    }

    private void sendActivity(SseEmitter emitter, String json) {
        SseEmitter.SseEventBuilder event = SseEmitter.event();
        event.name("activity");
        event.data(json);
        if (!SseEmitters.send(emitter, event)) {
            emitters.remove(emitter);
        }
    }

    private void sendHeartbeat(SseEmitter emitter) {
        SseEmitter.SseEventBuilder ping = SseEmitter.event();
        ping.comment("ping");
        if (!SseEmitters.send(emitter, ping)) {
            emitters.remove(emitter);
        }
    }

    private void drop(SseEmitter emitter) {
        emitters.remove(emitter);
        SseEmitters.complete(emitter);
    }

    @PreDestroy
    void shutdown() {
        heartbeat.shutdownNow();
        for (SseEmitter emitter : emitters.keySet()) {
            drop(emitter);
        }
    }
}
