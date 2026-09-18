package com.ivan.nexus.infrastructure.persistence.env;

import com.ivan.nexus.application.env.StoredProjectEnvVar;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JpaProjectEnvStoreTest {

    @Test
    void listsAndMapsEntities() {
        ProjectEnvVarJpaRepository repository = mock(ProjectEnvVarJpaRepository.class);
        UUID id = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        Instant now = Instant.parse("2026-09-18T10:00:00Z");
        when(repository.findByProjectIdOrderByNameAsc("lab")).thenReturn(List.of(
                new ProjectEnvVarEntity(id, "lab", "API_KEY", "cipher", true, now, now)));

        List<StoredProjectEnvVar> listed = new JpaProjectEnvStore(repository).listByProject("lab");

        assertThat(listed).hasSize(1);
        assertThat(listed.getFirst().name()).isEqualTo("API_KEY");
        assertThat(listed.getFirst().encryptedValue()).isEqualTo("cipher");
        assertThat(listed.getFirst().secret()).isTrue();
    }

    @Test
    void upsertsNewVariable() {
        ProjectEnvVarJpaRepository repository = mock(ProjectEnvVarJpaRepository.class);
        when(repository.findByProjectIdAndName("lab", "NODE_ENV")).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
        Instant now = Instant.parse("2026-09-18T10:00:00Z");
        UUID id = UUID.randomUUID();

        StoredProjectEnvVar saved = new JpaProjectEnvStore(repository).upsert(new StoredProjectEnvVar(
                id, "lab", "NODE_ENV", "cipher-prod", false, now, now));

        ArgumentCaptor<ProjectEnvVarEntity> captor = ArgumentCaptor.forClass(ProjectEnvVarEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("NODE_ENV");
        assertThat(captor.getValue().getEncryptedValue()).isEqualTo("cipher-prod");
        assertThat(saved.encryptedValue()).isEqualTo("cipher-prod");
    }
}
