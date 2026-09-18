package com.ivan.nexus.infrastructure.backup;

import com.ivan.nexus.application.backup.BackupProvider;
import com.ivan.nexus.application.database.DiscoverProjectDatabases;
import com.ivan.nexus.infrastructure.config.NexusProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NexusProperties.class)
public class BackupConfiguration {

    @Bean
    @ConditionalOnProperty(name = "nexus.backup.enabled", havingValue = "true")
    BackupProvider pgDumpBackupProvider(DiscoverProjectDatabases databases, NexusProperties properties) {
        return new PgDumpBackupProvider(databases, properties.getBackup());
    }

    @Bean
    @ConditionalOnProperty(name = "nexus.backup.enabled", havingValue = "false", matchIfMissing = true)
    BackupProvider noOpBackupProvider() {
        return new NoOpBackupProvider();
    }
}
