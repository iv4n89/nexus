package com.ivan.nexus.interfaces.settings;

import com.ivan.nexus.infrastructure.config.NexusProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SettingsController {
    private final String version;
    private final NexusProperties properties;

    public SettingsController(
            @Value("${nexus.version:0.0.1-SNAPSHOT}") String version,
            NexusProperties properties) {
        this.version = version;
        this.properties = properties;
    }

    @GetMapping("/api/settings")
    public SettingsResponse settings() {
        NexusProperties.Retention retention = properties.getRetention();
        return new SettingsResponse(
                version,
                new RetentionResponse(
                        retention.getActivityDays(),
                        retention.getDeploymentEventsDays(),
                        retention.getFingerprintDays()));
    }

    public record SettingsResponse(String version, RetentionResponse retention) {}

    public record RetentionResponse(int activityDays, int deploymentEventsDays, int fingerprintDays) {}
}
