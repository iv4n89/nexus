package com.ivan.nexus.application.traffic;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.deployment.DeploymentStore;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.deployment.Deployment;
import com.ivan.nexus.domain.deployment.DeploymentStatus;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.domain.traffic.DeployTrafficDelta;
import com.ivan.nexus.domain.traffic.TrafficSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Compares traffic 5xx rate and p95 latency before vs after a deployment window.
 */
@Service
public class CorrelateDeployTraffic {
    public static final Duration WINDOW = Duration.ofHours(1);

    private final DeploymentStore deployments;
    private final TrafficStore traffic;
    private final RecordActivity recordActivity;
    private final Clock clock;

    public CorrelateDeployTraffic(
            DeploymentStore deployments,
            TrafficStore traffic,
            RecordActivity recordActivity,
            Clock clock) {
        this.deployments = deployments;
        this.traffic = traffic;
        this.recordActivity = recordActivity;
        this.clock = clock;
    }

    @Transactional
    public DeployTrafficDelta execute(String projectId, UUID deploymentId) {
        Deployment deployment = resolveDeployment(projectId, deploymentId);
        Instant pivot = deployment.finishedAt() != null
                ? deployment.finishedAt()
                : (deployment.startedAt() != null ? deployment.startedAt() : clock.instant());
        Instant beforeFrom = pivot.minus(WINDOW);
        Instant afterTo = pivot.plus(WINDOW);

        TrafficSnapshot before = traffic.snapshot(projectId, beforeFrom, pivot);
        TrafficSnapshot after = traffic.snapshot(projectId, pivot, afterTo);
        DeployTrafficDelta delta = DeployTrafficDelta.of(
                projectId,
                deployment.id(),
                pivot,
                beforeFrom,
                pivot,
                pivot,
                afterTo,
                before,
                after);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("deploymentId", deployment.id().toString());
        metadata.put("status5xxDeltaRate", delta.status5xxDeltaRate());
        metadata.put("latencyP95DeltaMs", delta.latencyP95DeltaMs());
        metadata.put("trafficDeltaPercent", delta.trafficDeltaPercent());
        metadata.put("beforeStatus5xx", delta.beforeStatus5xx());
        metadata.put("afterStatus5xx", delta.afterStatus5xx());
        recordActivity.execute(
                ActivityType.TRAFFIC_DEPLOY_DELTA,
                projectId,
                null,
                "post-deploy traffic delta",
                metadata);

        return delta;
    }

    private Deployment resolveDeployment(String projectId, UUID deploymentId) {
        if (deploymentId != null) {
            Deployment deployment = deployments
                    .findById(deploymentId)
                    .orElseThrow(() -> new DomainException(
                            NexusErrorCode.DEPLOYMENT_NOT_FOUND, "Deployment not found"));
            if (!projectId.equals(deployment.projectId())) {
                throw new DomainException(NexusErrorCode.DEPLOYMENT_NOT_FOUND, "Deployment not found");
            }
            return deployment;
        }
        return deployments.findProjectHistoryNewestFirst(projectId).stream()
                .filter(d -> d.status() == DeploymentStatus.SUCCESS)
                .findFirst()
                .orElseThrow(() -> new DomainException(
                        NexusErrorCode.DEPLOYMENT_NOT_FOUND, "No successful deployment found"));
    }
}
