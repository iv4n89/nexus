package com.ivan.nexus.application.backup;

import com.ivan.nexus.application.manifest.ManifestCatalog;
import com.ivan.nexus.domain.backup.BackupKind;
import com.ivan.nexus.domain.backup.BackupPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScheduledBackupTest {

    @Mock
    ManifestCatalog manifests;
    @Mock
    BackupPolicyStore policies;
    @Mock
    RunBackup runBackup;
    @InjectMocks
    ScheduledBackup scheduled;

    @Test
    void runsOnlyEnabledPolicies() {
        given(manifests.discoverProjectIds()).willReturn(Set.of("lab", "demo", "off"));
        given(policies.findByProjectId("lab"))
                .willReturn(Optional.of(new BackupPolicy("lab", 7, 4, 3, true, "0 2 * * *")));
        given(policies.findByProjectId("demo")).willReturn(Optional.empty());
        given(policies.findByProjectId("off"))
                .willReturn(Optional.of(new BackupPolicy("off", 7, 4, 3, false, null)));

        scheduled.execute();

        verify(runBackup).execute("lab", BackupKind.SCHEDULED);
        verify(runBackup, never()).execute("demo", BackupKind.SCHEDULED);
        verify(runBackup, never()).execute("off", BackupKind.SCHEDULED);
    }
}
