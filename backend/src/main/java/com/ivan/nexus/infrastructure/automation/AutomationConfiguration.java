package com.ivan.nexus.infrastructure.automation;

import com.ivan.nexus.application.automation.RunDeployPipeline;
import com.ivan.nexus.infrastructure.config.NexusProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NexusProperties.class)
public class AutomationConfiguration {

    @Bean
    RunDeployPipeline.PipelineFlags pipelineFlags(NexusProperties properties) {
        NexusProperties.Automation automation = properties.getAutomation();
        return new RunDeployPipeline.PipelineFlags(
                automation.isSecurityGateEnabled(),
                automation.isTrafficWatchEnabled(),
                automation.isPostDeployBackupEnabled());
    }
}
