package com.ivan.nexus.infrastructure.sse;

import com.ivan.nexus.domain.deployment.DeploymentStatus;
import com.ivan.nexus.infrastructure.persistence.deployment.DeploymentEntity;
import com.ivan.nexus.infrastructure.persistence.deployment.DeploymentEventEntity;
import com.ivan.nexus.infrastructure.persistence.deployment.DeploymentEventJpaRepository;
import com.ivan.nexus.infrastructure.persistence.deployment.DeploymentJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class DeploymentStreamHub {
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final DeploymentEventJpaRepository events;
    private final DeploymentJpaRepository deployments;

    public DeploymentStreamHub(DeploymentEventJpaRepository events, DeploymentJpaRepository deployments) {
        this.events = events;
        this.deployments = deployments;
    }

    public void subscribe(UUID id, SseEmitter emitter) {
        List<DeploymentEventEntity> history = events.findByDeploymentIdOrderByIdAsc(id);
        for (DeploymentEventEntity event : history) {
            send(emitter, event.getLine());
        }

        DeploymentEntity deployment = deployments.findById(id).orElse(null);
        if (deployment != null && isTerminal(deployment.getStatus())) {
            completeEmitter(emitter);
            return;
        }

        emitters.computeIfAbsent(id, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(id, emitter));
        emitter.onTimeout(() -> remove(id, emitter));
        emitter.onError(error -> remove(id, emitter));
    }

    public void append(UUID id, String line) {
        events.save(new DeploymentEventEntity(id, line));
        CopyOnWriteArrayList<SseEmitter> live = emitters.get(id);
        if (live == null) {
            return;
        }
        for (SseEmitter emitter : live) {
            send(emitter, line);
        }
    }

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
        synchronized (emitter) {
            try {
                emitter.send(SseEmitter.event().name("log").data(line));
            } catch (IOException ex) {
                completeEmitter(emitter);
            }
        }
    }

    private static void completeEmitter(SseEmitter emitter) {
        synchronized (emitter) {
            emitter.complete();
        }
    }

    private static boolean isTerminal(DeploymentStatus status) {
        return status == DeploymentStatus.SUCCESS
                || status == DeploymentStatus.FAILED
                || status == DeploymentStatus.CANCELLED;
    }
}
