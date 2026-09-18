package com.ivan.nexus.infrastructure.backup;

import com.ivan.nexus.application.backup.BackupProvider;
import com.ivan.nexus.application.backup.BackupRestoreProvider;
import com.ivan.nexus.application.backup.BackupStorage;
import com.ivan.nexus.application.backup.PostRestoreHealthCheck;
import com.ivan.nexus.application.backup.VolumeBackupProvider;
import com.ivan.nexus.application.database.DiscoverProjectDatabases;
import com.ivan.nexus.application.deployment.HealthChecker;
import com.ivan.nexus.application.manifest.ManifestCatalog;
import com.ivan.nexus.infrastructure.config.NexusProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

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

    @Bean
    @ConditionalOnProperty(name = "nexus.backup.enabled", havingValue = "true")
    VolumeBackupProvider tarVolumeBackupProvider(NexusProperties properties) {
        return new TarVolumeBackupProvider(properties.getBackup());
    }

    @Bean
    @ConditionalOnProperty(name = "nexus.backup.enabled", havingValue = "false", matchIfMissing = true)
    VolumeBackupProvider noOpVolumeBackupProvider() {
        return new NoOpVolumeBackupProvider();
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "nexus.backup.s3.enabled", havingValue = "false", matchIfMissing = true)
    BackupStorage localBackupStorage(NexusProperties properties) {
        return new LocalBackupStorage(properties.getBackup().getLocalPath());
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "nexus.backup.s3.enabled", havingValue = "true")
    BackupStorage s3CompatibleBackupStorage(NexusProperties properties) {
        NexusProperties.Backup.S3 s3 = properties.getBackup().getS3();
        return new S3CompatibleBackupStorage(s3.getEndpoint(), s3.getBucket());
    }

    @Bean
    @ConditionalOnProperty(name = "nexus.backup.enabled", havingValue = "true")
    BackupRestoreProvider processBackupRestoreProvider(NexusProperties properties) {
        return new ProcessBackupRestoreProvider(properties.getBackup().getPgRestoreExecutable());
    }

    @Bean
    @ConditionalOnProperty(name = "nexus.backup.enabled", havingValue = "false", matchIfMissing = true)
    BackupRestoreProvider noOpBackupRestoreProvider() {
        return new NoOpBackupRestoreProvider();
    }

    @Bean
    PostRestoreHealthCheck manifestPostRestoreHealthCheck(
            ManifestCatalog manifests,
            HealthChecker healthChecker) {
        return new ManifestPostRestoreHealthCheck(manifests, healthChecker);
    }
}
