package com.ivan.nexus.application.log;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class SearchLogs {
    public List<String> execute(List<String> lines, String query, String level) {
        boolean filterQuery = hasText(query);
        boolean filterLevel = hasText(level) && !"ALL".equalsIgnoreCase(level.trim());
        String queryNeedle = filterQuery ? query.trim().toLowerCase(Locale.ROOT) : null;
        String levelNeedle = filterLevel ? level.trim().toLowerCase(Locale.ROOT) : null;

        return lines.stream()
                .filter(line -> !filterQuery || line.toLowerCase(Locale.ROOT).contains(queryNeedle))
                .filter(line -> !filterLevel || matchesLevel(line, levelNeedle))
                .toList();
    }

    static boolean matchesLevel(String line, String levelNeedle) {
        String haystack = line.toLowerCase(Locale.ROOT);
        return switch (levelNeedle) {
            case "error" -> haystack.contains("error")
                    || haystack.contains("exception")
                    || haystack.contains("fatal")
                    || haystack.contains("traceback")
                    || haystack.contains("emerg");
            case "warn" -> haystack.contains("warn");
            default -> haystack.contains(levelNeedle);
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
