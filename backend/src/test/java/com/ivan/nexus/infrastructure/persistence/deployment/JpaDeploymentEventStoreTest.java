package com.ivan.nexus.infrastructure.persistence.deployment;

import com.ivan.nexus.application.deployment.DeploymentEventStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JpaDeploymentEventStoreTest {

    @Test
    void deleteCreatedBeforeDelegates() {
        DeploymentEventJpaRepository repository = mock(DeploymentEventJpaRepository.class);
        DeploymentEventStore store = new JpaDeploymentEventStore(repository);
        Instant cutoff = Instant.parse("2026-09-01T03:00:00Z");
        when(repository.deleteByCreatedAtBefore(cutoff)).thenReturn(4L);

        assertThat(store.deleteCreatedBefore(cutoff)).isEqualTo(4L);
        verify(repository).deleteByCreatedAtBefore(cutoff);
    }
}
