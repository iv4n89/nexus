package com.ivan.nexus.interfaces.activity;

import com.ivan.nexus.application.activity.GetActivityTimeline;
import com.ivan.nexus.infrastructure.sse.ActivityHub;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
public class ActivityController {
    private final GetActivityTimeline getActivityTimeline;
    private final ActivityHub hub;

    public ActivityController(GetActivityTimeline getActivityTimeline, ActivityHub hub) {
        this.getActivityTimeline = getActivityTimeline;
        this.hub = hub;
    }

    @GetMapping("/api/activity")
    public List<ActivityResponse> list(@RequestParam(defaultValue = "50") int limit) {
        return getActivityTimeline.execute(limit);
    }

    @GetMapping(path = "/api/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        SseEmitter emitter = new SseEmitter(0L);
        hub.subscribe(emitter);
        return emitter;
    }
}
