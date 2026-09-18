package com.ivan.nexus.interfaces.traffic;

import com.ivan.nexus.application.traffic.CorrelateDeployTraffic;
import com.ivan.nexus.application.traffic.GetProjectTraffic;
import com.ivan.nexus.domain.traffic.DeployTrafficDelta;
import com.ivan.nexus.domain.traffic.TrafficEndpointStat;
import com.ivan.nexus.domain.traffic.TrafficSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
public class TrafficController {
    private final GetProjectTraffic getProjectTraffic;
    private final CorrelateDeployTraffic correlateDeployTraffic;

    public TrafficController(
            GetProjectTraffic getProjectTraffic, CorrelateDeployTraffic correlateDeployTraffic) {
        this.getProjectTraffic = getProjectTraffic;
        this.correlateDeployTraffic = correlateDeployTraffic;
    }

    @GetMapping("/api/projects/{id}/traffic")
    public TrafficResponse traffic(
            @PathVariable("id") String projectId,
            @RequestParam(defaultValue = "24") int hours) {
        return TrafficResponse.from(getProjectTraffic.execute(projectId, hours));
    }

    @GetMapping("/api/projects/{id}/traffic/deploy-delta")
    public DeployDeltaResponse deployDelta(
            @PathVariable("id") String projectId,
            @RequestParam(required = false) UUID deploymentId) {
        return DeployDeltaResponse.from(correlateDeployTraffic.execute(projectId, deploymentId));
    }

    public record TrafficResponse(
            String projectId,
            Instant from,
            Instant to,
            long requests,
            long bytesIn,
            long bytesOut,
            long status2xx,
            long status3xx,
            long status4xx,
            long status5xx,
            double latencyAvgMs,
            Double latencyP95Ms,
            List<EndpointStatResponse> topEndpoints) {
        static TrafficResponse from(TrafficSnapshot snapshot) {
            return new TrafficResponse(
                    snapshot.projectId(),
                    snapshot.from(),
                    snapshot.to(),
                    snapshot.requests(),
                    snapshot.bytesIn(),
                    snapshot.bytesOut(),
                    snapshot.status2xx(),
                    snapshot.status3xx(),
                    snapshot.status4xx(),
                    snapshot.status5xx(),
                    snapshot.latencyAvgMs(),
                    snapshot.latencyP95Ms(),
                    snapshot.topEndpoints().stream().map(EndpointStatResponse::from).toList());
        }
    }

    public record EndpointStatResponse(String path, long requests, double latencyAvgMs) {
        static EndpointStatResponse from(TrafficEndpointStat stat) {
            return new EndpointStatResponse(stat.path(), stat.requests(), stat.latencyAvgMs());
        }
    }

    public record DeployDeltaResponse(
            String projectId,
            UUID deploymentId,
            Instant deployedAt,
            Instant beforeFrom,
            Instant beforeTo,
            Instant afterFrom,
            Instant afterTo,
            long beforeRequests,
            long afterRequests,
            long beforeStatus5xx,
            long afterStatus5xx,
            Double beforeLatencyP95Ms,
            Double afterLatencyP95Ms,
            double status5xxDeltaRate,
            Double latencyP95DeltaMs,
            double trafficDeltaPercent) {
        static DeployDeltaResponse from(DeployTrafficDelta delta) {
            return new DeployDeltaResponse(
                    delta.projectId(),
                    delta.deploymentId(),
                    delta.deployedAt(),
                    delta.beforeFrom(),
                    delta.beforeTo(),
                    delta.afterFrom(),
                    delta.afterTo(),
                    delta.beforeRequests(),
                    delta.afterRequests(),
                    delta.beforeStatus5xx(),
                    delta.afterStatus5xx(),
                    delta.beforeLatencyP95Ms(),
                    delta.afterLatencyP95Ms(),
                    delta.status5xxDeltaRate(),
                    delta.latencyP95DeltaMs(),
                    delta.trafficDeltaPercent());
        }
    }
}
