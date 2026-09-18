package com.ivan.nexus.application.traffic;

import com.ivan.nexus.application.site.DomainStore;
import com.ivan.nexus.domain.traffic.TrafficMinuteBucket;
import org.springframework.stereotype.Service;

@Service
public class ResolveTrafficTarget {

    private final DomainStore domains;

    public ResolveTrafficTarget(DomainStore domains) {
        this.domains = domains;
    }

    public record ResolvedTarget(String projectId, String serviceId, String host) {}

    public ResolvedTarget execute(String rawHost) {
        String host = TrafficMinuteBucket.normalizeHost(stripPort(rawHost));
        if (host.isBlank()) {
            return new ResolvedTarget(
                    TrafficMinuteBucket.UNMAPPED_PROJECT,
                    TrafficMinuteBucket.UNKNOWN_SERVICE,
                    "");
        }
        return domains.findByHostname(host)
                .map(d -> new ResolvedTarget(
                        d.projectId(),
                        TrafficMinuteBucket.normalizeServiceId(d.serviceName()),
                        host))
                .orElse(new ResolvedTarget(
                        TrafficMinuteBucket.UNMAPPED_PROJECT,
                        TrafficMinuteBucket.UNKNOWN_SERVICE,
                        host));
    }

    private static String stripPort(String rawHost) {
        if (rawHost == null) {
            return null;
        }
        int lastColon = rawHost.lastIndexOf(':');
        if (lastColon < 0) {
            return rawHost;
        }
        String suffix = rawHost.substring(lastColon + 1);
        if (suffix.chars().allMatch(Character::isDigit)) {
            return rawHost.substring(0, lastColon);
        }
        return rawHost;
    }
}
