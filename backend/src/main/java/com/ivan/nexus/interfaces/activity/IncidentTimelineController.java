package com.ivan.nexus.interfaces.activity;

import com.ivan.nexus.application.activity.GetIncidentTimeline;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
public class IncidentTimelineController {
    private final GetIncidentTimeline getIncidentTimeline;

    public IncidentTimelineController(GetIncidentTimeline getIncidentTimeline) {
        this.getIncidentTimeline = getIncidentTimeline;
    }

    @GetMapping("/api/projects/{id}/timeline")
    public List<TimelineItemResponse> timeline(
            @PathVariable String id, @RequestParam(defaultValue = "50") int limit) {
        return getIncidentTimeline.execute(id, limit).stream()
                .map(TimelineItemResponse::from)
                .toList();
    }

    public record TimelineItemResponse(
            Instant at,
            String kind,
            String source,
            String message,
            String serviceId,
            UUID refId) {

        static TimelineItemResponse from(GetIncidentTimeline.Item item) {
            return new TimelineItemResponse(
                    item.at(),
                    item.kind(),
                    item.source(),
                    item.message(),
                    item.serviceId(),
                    item.refId());
        }
    }
}
