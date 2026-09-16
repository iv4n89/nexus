package com.ivan.nexus.infrastructure.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.AsyncDockerCmd;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Frame;
import com.ivan.nexus.application.log.LogProvider;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

@Component
public class DockerLogProvider implements LogProvider {
    private static final Logger log = LoggerFactory.getLogger(DockerLogProvider.class);
    private static final long LOGS_TIMEOUT_SECONDS = 10;

    private final DockerClient dockerClient;

    public DockerLogProvider(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    @Override
    public List<String> fetch(String containerId, int tail, Integer since, Integer until, boolean timestamps) {
        try (LogCollector callback = new LogCollector()) {
            LogContainerCmd command = dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withFollowStream(false)
                    .withTimestamps(timestamps)
                    .withTail(tail);
            if (since != null) {
                command.withSince(since);
            }
            if (until != null) {
                command.withUntil(until);
            }
            run(command, callback);
            boolean completed = callback.awaitCompletion(LOGS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                log.warn("Log fetch timed out for container {}", containerId);
            }
            return List.copyOf(callback.lines);
        } catch (NotFoundException ex) {
            throw new DomainException(NexusErrorCode.CONTAINER_NOT_FOUND, "Container not found");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while fetching logs for container {}", containerId);
            return List.of();
        } catch (IOException ex) {
            log.warn("Log fetch failed for container {}", containerId, ex);
            throw new IllegalStateException("Failed to fetch container logs", ex);
        }
    }

    private static <T extends ResultCallback<Frame>> T run(
            AsyncDockerCmd<LogContainerCmd, Frame> command, T callback) {
        return command.exec
                (callback);
    }

    static final class LogCollector extends ResultCallback.Adapter<Frame> {
        private final List<String> lines = new CopyOnWriteArrayList<>();

        @Override
        public void onNext(Frame frame) {
            if (frame == null || frame.getPayload() == null || frame.getPayload().length == 0) {
                return;
            }
            new String(frame.getPayload(), StandardCharsets.UTF_8)
                    .lines()
                    .forEach(lines::add);
        }
    }
}
