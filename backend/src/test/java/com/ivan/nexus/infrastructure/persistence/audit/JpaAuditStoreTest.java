package com.ivan.nexus.infrastructure.persistence.audit;

import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.audit.AuditEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JpaAuditStoreTest {

    @Test
    void mapsAuditEventToJpaEntity() {
        AuditEventJpaRepository repository = mock(AuditEventJpaRepository.class);
        UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Instant createdAt = Instant.parse("2026-09-17T20:00:00Z");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("nullable", null);
        AuditEvent event = new AuditEvent(
                id, userId, AuditAction.DEPLOY, "lab", "api", "127.0.0.1", metadata, createdAt);

        new JpaAuditStore(repository).append(event);

        ArgumentCaptor<AuditEventEntity> entityCaptor = ArgumentCaptor.forClass(AuditEventEntity.class);
        verify(repository).save(entityCaptor.capture());
        AuditEventEntity entity = entityCaptor.getValue();
        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getUserId()).isEqualTo(userId);
        assertThat(entity.getAction()).isEqualTo(AuditAction.DEPLOY);
        assertThat(entity.getProjectId()).isEqualTo("lab");
        assertThat(entity.getServiceId()).isEqualTo("api");
        assertThat(entity.getIp()).isEqualTo("127.0.0.1");
        assertThat(entity.getMetadata()).containsEntry("nullable", null);
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
    }
}
