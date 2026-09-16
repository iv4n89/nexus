package com.ivan.nexus.interfaces.log;

import com.ivan.nexus.application.log.GetContainerLogs;
import com.ivan.nexus.application.log.SearchLogs;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class LogController {
    private final GetContainerLogs getContainerLogs;
    private final SearchLogs searchLogs;

    public LogController(GetContainerLogs getContainerLogs, SearchLogs searchLogs) {
        this.getContainerLogs = getContainerLogs;
        this.searchLogs = searchLogs;
    }

    @GetMapping("/api/containers/{id}/logs")
    public LogResponse logs(
            @PathVariable String id,
            @RequestParam(defaultValue = "200") int tail,
            @RequestParam(required = false) Integer since,
            @RequestParam(required = false) Integer until,
            @RequestParam(defaultValue = "true") boolean timestamps) {
        return new LogResponse(getContainerLogs.execute(id, query(tail, since, until, timestamps)));
    }

    @GetMapping("/api/containers/{id}/logs/search")
    public LogResponse search(
            @PathVariable String id,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "200") int tail,
            @RequestParam(required = false) Integer since,
            @RequestParam(required = false) Integer until,
            @RequestParam(defaultValue = "true") boolean timestamps) {
        List<String> lines = getContainerLogs.execute(id, query(tail, since, until, timestamps));
        return new LogResponse(searchLogs.execute(lines, q, level));
    }

    private static GetContainerLogs.LogQuery query(int tail, Integer since, Integer until, boolean timestamps) {
        return new GetContainerLogs.LogQuery(tail, since, until, timestamps);
    }

    public record LogResponse(List<String> lines) {
    }
}
