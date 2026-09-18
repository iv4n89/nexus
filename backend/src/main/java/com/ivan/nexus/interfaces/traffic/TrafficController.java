package com.ivan.nexus.interfaces.traffic;

import com.ivan.nexus.application.traffic.CorrelateDeployTraffic;
import com.ivan.nexus.application.traffic.GetGlobalTraffic;
import com.ivan.nexus.application.traffic.GetProjectTraffic;
import com.ivan.nexus.domain.traffic.DeployTrafficDelta;
import com.ivan.nexus.domain.traffic.TrafficEndpointStat;
import com.ivan.nexus.domain.traffic.TrafficOverview;
import com.ivan.nexus.domain.traffic.TrafficProjectRanking;
import com.ivan.nexus.domain.traffic.TrafficReport;
import com.ivan.nexus.domain.traffic.TrafficSeriesPoint;
import com.ivan.nexus.domain.traffic.TrafficServiceBreakdown;
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
    private final GetGlobalTraffic getGlobalTraffic;
    private final CorrelateDeployTraffic correlateDeployTraffic;

    public TrafficController(
            GetProjectTraffic getProjectTraffic,
            GetGlobalTraffic getGlobalTraffic,
            CorrelateDeployTraffic correlateDeployTraffic) {
        this.getProjectTraffic = getProjectTraffic;
        this.getGlobalTraffic = getGlobalTraffic;
        this.correlateDeployTraffic = correlateDeployTraffic;
    }

    @GetMapping("/api/traffic")
    public GlobalTrafficResponse global(@RequestParam(defaultValue = "24") int hours) {
        return GlobalTrafficResponse.from(getGlobalTraffic.execute(hours), hours);
    }

    @GetMapping("/api/projects/{id}/traffic")
    public ProjectTrafficResponse traffic(
            @PathVariable("id") String projectId,
            @RequestParam(defaultValue = "24") int hours) {
        return ProjectTrafficResponse.from(getProjectTraffic.execute(projectId, hours));
    }

    @GetMapping("/api/projects/{id}/traffic/deploy-delta")
    public DeployDeltaResponse deployDelta(
            @PathVariable("id") String projectId,
            @RequestParam(required = false) UUID deploymentId) {
        return DeployDeltaResponse.from(correlateDeployTraffic.execute(projectId, deploymentId));
    }

    public record ProjectTrafficResponse(
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
            Double latencyMaxMs,
            List<EndpointStatResponse> topEndpoints,
            List<SeriesPointResponse> series,
            List<ServiceBreakdownResponse> services) {
        static ProjectTrafficResponse from(TrafficReport report) {
            TrafficSnapshot totals = report.totals();
            Double latencyMaxMs = totals.latencyP95Ms();
            return new ProjectTrafficResponse(
                    report.projectId(),
                    report.from(),
                    report.to(),
                    totals.requests(),
                    totals.bytesIn(),
                    totals.bytesOut(),
                    totals.status2xx(),
                    totals.status3xx(),
                    totals.status4xx(),
                    totals.status5xx(),
                    totals.latencyAvgMs(),
                    totals.latencyP95Ms(),
                    latencyMaxMs,
                    totals.topEndpoints().stream().map(EndpointStatResponse::from).toList(),
                    report.series().stream().map(SeriesPointResponse::from).toList(),
                    report.services().stream().map(ServiceBreakdownResponse::from).toList());
        }
    }

    public record GlobalTrafficResponse(
            Instant from,
            Instant to,
            int hours,
            long requests,
            long bytesOut,
            long status2xx,
            long status4xx,
            long status5xx,
            double latencyAvgMs,
            Double latencyMaxMs,
            List<SeriesPointResponse> series,
            List<ProjectRankingResponse> projects) {
        static GlobalTrafficResponse from(TrafficOverview overview, int hours) {
            TrafficSnapshot totals = overview.totals();
            return new GlobalTrafficResponse(
                    overview.from(),
                    overview.to(),
                    hours,
                    totals.requests(),
                    totals.bytesOut(),
                    totals.status2xx(),
                    totals.status4xx(),
                    totals.status5xx(),
                    totals.latencyAvgMs(),
                    totals.latencyP95Ms(),
                    overview.series().stream().map(SeriesPointResponse::from).toList(),
                    overview.projects().stream().map(ProjectRankingResponse::from).toList());
        }
    }

    public record SeriesPointResponse(
            Instant t,
            long requests,
            long status5xx,
            double latencyAvgMs,
            Double latencyMaxMs) {
        static SeriesPointResponse from(TrafficSeriesPoint point) {
            return new SeriesPointResponse(
                    point.t(),
                    point.requests(),
                    point.status5xx(),
                    point.latencyAvgMs(),
                    point.latencyMaxMs());
        }
    }

    public record ServiceBreakdownResponse(
            String serviceId,
            long requests,
            long status5xx,
            double latencyAvgMs,
            Double latencyMaxMs) {
        static ServiceBreakdownResponse from(TrafficServiceBreakdown service) {
            return new ServiceBreakdownResponse(
                    service.serviceId(),
                    service.requests(),
                    service.status5xx(),
                    service.latencyAvgMs(),
                    service.latencyMaxMs());
        }
    }

    public record ProjectRankingResponse(
            String projectId, long requests, long status5xx, double latencyAvgMs) {
        static ProjectRankingResponse from(TrafficProjectRanking ranking) {
            return new ProjectRankingResponse(
                    ranking.projectId(), ranking.requests(), ranking.status5xx(), ranking.latencyAvgMs());
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
