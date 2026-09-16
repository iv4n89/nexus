package com.ivan.nexus.infrastructure.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.AsyncDockerCmd;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.model.Statistics;
import com.ivan.nexus.application.metrics.ContainerStatsProvider;
import com.ivan.nexus.domain.metrics.ContainerMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class DockerStatsProvider implements ContainerStatsProvider {
    private static final Logger log = LoggerFactory.getLogger(DockerStatsProvider.class);
    private static final long STATS_TIMEOUT_SECONDS = 10;

    private final DockerClient dockerClient;

    public DockerStatsProvider(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    @Override
    public ContainerMetrics stats(String containerId) {
        try (StatsCollector callback = new StatsCollector()) {
            run(dockerClient.statsCmd(containerId).withNoStream(true), callback);
            boolean completed = callback.awaitCompletion(STATS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed || callback.statistics == null) {
                log.warn("Stats unavailable for container {}", containerId);
                return DockerStatsCalculator.zeros(containerId);
            }
            return DockerStatsCalculator.toMetrics(containerId, callback.statistics);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for stats of container {}", containerId);
            return DockerStatsCalculator.zeros(containerId);
        } catch (Exception ex) {
            log.warn("Stats unavailable for container {}", containerId, ex);
            return DockerStatsCalculator.zeros(containerId);
        }
    }

    private static <T extends ResultCallback<Statistics>> T run(
            AsyncDockerCmd<StatsCmd, Statistics> command, T callback) {
        return command.exec
                (callback);
    }

    static final class StatsCollector extends ResultCallback.Adapter<Statistics> {
        private volatile Statistics statistics;

        @Override
        public void onNext(Statistics object) {
            this.statistics = object;
        }
    }
}
