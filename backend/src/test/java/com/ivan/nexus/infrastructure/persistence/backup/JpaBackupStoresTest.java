package com.ivan.nexus.infrastructure.persistence.backup;

import com.ivan.nexus.application.backup.BackupPolicyStore;
import com.ivan.nexus.application.backup.BackupStore;
import com.ivan.nexus.domain.backup.Backup;
import com.ivan.nexus.domain.backup.BackupKind;
import com.ivan.nexus.domain.backup.BackupPolicy;
import com.ivan.nexus.domain.backup.BackupStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JpaBackupPolicyStoreTest {
    private BackupPolicyJpaRepository repository;
    private BackupPolicyStore store;

    @BeforeEach
    void setUp() {
        repository = mock(BackupPolicyJpaRepository.class);
        store = new JpaBackupPolicyStore(repository);
    }

    @Test
    void findByProjectIdMapsEntity() {
        when(repository.findById("lab")).thenReturn(Optional.of(entity("lab", true)));

        assertThat(store.findByProjectId("lab")).contains(new BackupPolicy("lab", 7, 4, 3, true, "0 3 * * *"));
    }

    @Test
    void upsertCreatesWhenMissing() {
        when(repository.findById("lab")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BackupPolicy result = store.upsert(new BackupPolicy("lab", 10, 5, 2, true, null));

        ArgumentCaptor<BackupPolicyEntity> captor = ArgumentCaptor.forClass(BackupPolicyEntity.class);
        verify(repository).save(captor.capture());
        BackupPolicyEntity saved = captor.getValue();
        assertThat(saved.getProjectId()).isEqualTo("lab");
        assertThat(saved.getDailyRetention()).isEqualTo(10);
        assertThat(saved.getWeeklyRetention()).isEqualTo(5);
        assertThat(saved.getMonthlyRetention()).isEqualTo(2);
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getScheduleCron()).isNull();
        assertThat(result.projectId()).isEqualTo("lab");
        assertThat(result.dailyRetention()).isEqualTo(10);
    }

    @Test
    void upsertUpdatesExisting() {
        BackupPolicyEntity existing = entity("lab", false);
        when(repository.findById("lab")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        store.upsert(new BackupPolicy("lab", 14, 8, 6, true, "0 4 * * *"));

        assertThat(existing.getDailyRetention()).isEqualTo(14);
        assertThat(existing.getWeeklyRetention()).isEqualTo(8);
        assertThat(existing.getMonthlyRetention()).isEqualTo(6);
        assertThat(existing.isEnabled()).isTrue();
        assertThat(existing.getScheduleCron()).isEqualTo("0 4 * * *");
        verify(repository).save(existing);
    }

    private static BackupPolicyEntity entity(String projectId, boolean enabled) {
        return new BackupPolicyEntity(projectId, 7, 4, 3, enabled, "0 3 * * *");
    }
}

class JpaBackupStoreTest {
    private BackupJpaRepository repository;
    private BackupStore store;

    @BeforeEach
    void setUp() {
        repository = mock(BackupJpaRepository.class);
        store = new JpaBackupStore(repository);
    }

    @Test
    void findByProjectIdNewestFirstMapsEntities() {
        Backup newer = backup(Instant.parse("2026-09-18T10:00:00Z"));
        Backup older = backup(Instant.parse("2026-09-17T10:00:00Z"));
        when(repository.findByProjectIdOrderByCreatedAtDesc("lab"))
                .thenReturn(List.of(entity(newer), entity(older)));

        assertThat(store.findByProjectIdNewestFirst("lab")).containsExactly(newer, older);
    }

    @Test
    void saveMapsDomainToEntityAndBack() {
        Backup backup = backup(Instant.parse("2026-09-18T10:00:00Z"));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(store.save(backup)).isEqualTo(backup);

        ArgumentCaptor<BackupEntity> captor = ArgumentCaptor.forClass(BackupEntity.class);
        verify(repository).save(captor.capture());
        BackupEntity entity = captor.getValue();
        assertThat(entity.getId()).isEqualTo(backup.id());
        assertThat(entity.getProjectId()).isEqualTo("lab");
        assertThat(entity.getStatus()).isEqualTo(BackupStatus.SUCCESS);
        assertThat(entity.getKind()).isEqualTo(BackupKind.MANUAL);
        assertThat(entity.getArtifactUri()).isEqualTo("s3://bucket/lab.tar");
        assertThat(entity.getSummary()).isEqualTo("ok");
        assertThat(entity.isIncludesDb()).isTrue();
        assertThat(entity.isIncludesVolumes()).isFalse();
    }

    private static Backup backup(Instant createdAt) {
        return new Backup(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                "lab",
                BackupStatus.SUCCESS,
                BackupKind.MANUAL,
                createdAt,
                createdAt.plusSeconds(30),
                "s3://bucket/lab.tar",
                "ok",
                true,
                false);
    }

    private static BackupEntity entity(Backup backup) {
        return new BackupEntity(
                backup.id(),
                backup.projectId(),
                backup.status(),
                backup.kind(),
                backup.createdAt(),
                backup.finishedAt(),
                backup.artifactUri(),
                backup.summary(),
                backup.includesDb(),
                backup.includesVolumes());
    }
}
