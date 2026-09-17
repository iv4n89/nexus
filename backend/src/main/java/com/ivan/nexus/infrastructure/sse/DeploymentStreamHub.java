package com.ivan.nexus.infrastructure.sse;

import com.ivan.nexus.application.deployment.DeploymentEventStore;
import com.ivan.nexus.application.deployment.DeploymentProgress;
import com.ivan.nexus.application.deployment.DeploymentStore;
import com.ivan.nexus.domain.deployment.Deployment;
import com.ivan.nexus.domain.deployment.DeploymentStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class DeploymentStreamHub implements DeploymentProgress {
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final DeploymentEventStore events;
    private final DeploymentStore deployments;

    public DeploymentStreamHub(DeploymentEventStore events, DeploymentStore deployments) {
        this.events = events;
        this.deployments = deployments;
    }

    public void subscribe(UUID id, SseEmitter emitter) {
        List<String> history = events.findLinesOldestFirst(id);
        for (String line : history) {
            send(emitter, line);
        }

        Deployment deployment = deployments.findById(id).orElse(null);
        if (deployment != null && isTerminal(deployment.status())) {
            completeEmitter(emitter);
            return;
        }

        emitters.computeIfAbsent(id, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(id, emitter));
        emitter.onTimeout(() -> remove(id, emitter));
        emitter.onError(error -> remove(id, emitter));
    }

    @Override
    public void append(UUID id, String line) {
        events.append(id, line);
        CopyOnWriteArrayList<SseEmitter> live = emitters.get(id);
        if (live == null) {
            return;
        }
        for (SseEmitter emitter : live) {
            send(emitter, line);
        }
    }

    @Override
    public void complete(UUID id) {
        CopyOnWriteArrayList<SseEmitter> live = emitters.remove(id);
        if (live == null) {
            return;
        }
        for (SseEmitter emitter : live) {
            completeEmitter(emitter);
        }
    }

    private void remove(UUID id, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> live = emitters.get(id);
        if (live != null) {
            live.remove(emitter);
            if (live.isEmpty()) {
                emitters.remove(id, live);
            }
        }
    }

    private static void send(SseEmitter emitter, String line) {
        SseEmitters.send(emitter, SseEmitter.event().name("log").data(line));
    }

    private static void completeEmitter(SseEmitter emitter) {
        SseEmitters.complete(emitter);
    }

    private static boolean isTerminal(DeploymentStatus status) {
        return status == DeploymentStatus.SUCCESS
                || status == DeploymentStatus.FAILED
                || status == DeploymentStatus.CANCELLED;
    }
}
