package com.ivan.nexus.infrastructure.terminal;

import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.terminal.TerminalSessionManager;
import com.ivan.nexus.application.user.UserDirectory;
import com.ivan.nexus.infrastructure.config.NexusProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(NexusProperties.class)
@ConditionalOnProperty(name = "nexus.terminal.enabled", havingValue = "true")
public class TerminalBeansConfiguration {

    @Bean(destroyMethod = "shutdown")
    TerminalSessionManager terminalSessionManager(
            RecordAudit recordAudit,
            UserDirectory users,
            NexusProperties properties) {
        int minutes = Math.max(1, properties.getTerminal().getSessionTimeoutMinutes());
        return ProcessTerminalSessionManager.createDefault(recordAudit, users, Duration.ofMinutes(minutes));
    }
}
