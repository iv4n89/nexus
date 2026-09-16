package com.ivan.nexus.interfaces.log;

import com.ivan.nexus.application.log.LogProvider;
import com.ivan.nexus.application.project.ContainerInventory;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RestController
public class LogStreamController {
    private static final Logger log = LoggerFactory.getLogger(LogStreamController.class);
    static final int DEFAULT_TAIL = 100;
    static final int MAX_TAIL = 2000;
    private static final long HEARTBEAT_SECONDS = 15;

    private final ContainerInventory inventory;
    private final LogProvider logProvider;
    private final Executor sseExecutor;

    public LogStreamController(
            ContainerInventory inventory,
            LogProvider logProvider,
            @Qualifier("sseExecutor") Executor sseExecutor) {
        this.inventory = inventory;
        this.logProvider = logProvider;
        this.sseExecutor = sseExecutor;
    }

    @GetMapping(path = "/api/containers/{id}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @PathVariable String id,
            @RequestParam(defaultValue = "100") int tail,
            @RequestParam(required = false) Integer since,
            HttpServletResponse response) {
        if (inventory.findById(id).isEmpty()) {
            throw new DomainException(NexusErrorCode.CONTAINER_NOT_FOUND, "Container not found");
        }

        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");

        SseEmitter emitter = new SseEmitter(0L);
        int clampedTail = clampTail(tail);

        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sse-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        AtomicReference<AutoCloseable> followHandle = new AtomicReference<>();
        AtomicBoolean cleaned = new AtomicBoolean(false);

        Runnable cleanup = () -> {
            if (!cleaned.compareAndSet(false, true)) {
                return;
            }
            heartbeat.shutdownNow();
            AutoCloseable handle = followHandle.getAndSet(null);
            closeQuietly(id, handle);
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());

        sseExecutor.execute(() -> {
            heartbeat.scheduleAtFixedRate(
                    () -> sendHeartbeat(emitter),
                    HEARTBEAT_SECONDS,
                    HEARTBEAT_SECONDS,
                    TimeUnit.SECONDS);
            try {
                AutoCloseable handle = logProvider.follow(
                        id,
                        clampedTail,
                        since,
                        line -> sendLog(emitter, line),
                        () -> complete(emitter));
                followHandle.set(handle);
                if (cleaned.get()) {
                    closeQuietly(id, followHandle.getAndSet(null));
                }
            } catch (Exception ex) {
                log.warn("Failed to follow logs for container {}", id, ex);
                completeWithError(emitter, ex);
            }
        });

        return emitter;
    }

    private static void sendLog(SseEmitter emitter, String line) {
        synchronized (emitter) {
            try {
                emitter.send(SseEmitter.event().name("log").data(line));
            } catch (IOException ex) {
                complete(emitter);
            }
        }
    }

    private static void sendHeartbeat(SseEmitter emitter) {
        synchronized (emitter) {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException ex) {
                complete(emitter);
            }
        }
    }

    private static void complete(SseEmitter emitter) {
        synchronized (emitter) {
            emitter.complete();
        }
    }

    private static void completeWithError(SseEmitter emitter, Exception ex) {
        synchronized (emitter) {
            emitter.completeWithError(ex);
        }
    }

    private static void closeQuietly(String containerId, AutoCloseable handle) {
        if (handle == null) {
            return;
        }
        try {
            handle.close();
        } catch (Exception ex) {
            log.debug("Error closing log follow for container {}", containerId, ex);
        }
    }

    static int clampTail(int tail) {
        if (tail < 1) {
            return DEFAULT_TAIL;
        }
        return Math.min(tail, MAX_TAIL);
    }
}
