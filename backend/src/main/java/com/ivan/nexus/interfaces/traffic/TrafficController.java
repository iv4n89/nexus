package com.ivan.nexus.interfaces.traffic;

import com.ivan.nexus.application.traffic.GetProjectTraffic;
import com.ivan.nexus.domain.traffic.TrafficSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
public class TrafficController {
    private final GetProjectTraffic getProjectTraffic;

    public TrafficController(GetProjectTraffic getProjectTraffic) {
        this.getProjectTraffic = getProjectTraffic;
    }

    @GetMapping("/api/projects/{id}/traffic")
    public TrafficResponse traffic(
            @PathVariable("id") String projectId,
            @RequestParam(defaultValue = "24") int hours) {
        return TrafficResponse.from(getProjectTraffic.execute(projectId, hours));
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
            double latencyAvg,
            Double latencyP95) {
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
                    snapshot.latencyAvg(),
                    snapshot.latencyP95());
        }
    }
}
