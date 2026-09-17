package com.ivan.nexus.application.audit;

import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.audit.AuditEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RecordAuditTest {

    @Test
    void appendsSanitizedImmutableEventWithoutMutatingCallerMetadata() {
        AuditStore store = mock(AuditStore.class);
        RecordAudit recordAudit = new RecordAudit(store);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("password", "secret");
        metadata.put("nullable", null);

        recordAudit.execute(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                AuditAction.LOGIN,
                "lab",
                "api",
                "127.0.0.1",
                metadata);

        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(store).append(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        assertThat(event.id()).isNotNull();
        assertThat(event.createdAt()).isNotNull();
        assertThat(event.metadata()).containsEntry("nullable", null).doesNotContainKey("password");
        assertThat(metadata).containsEntry("password", "secret").containsEntry("nullable", null);
        assertThatThrownBy(() -> event.metadata().put("other", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullMetadataBecomesEmptyMap() {
        AuditStore store = mock(AuditStore.class);
        RecordAudit recordAudit = new RecordAudit(store);

        recordAudit.execute(null, AuditAction.DB_QUERY, null, null, null, null);

        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(store).append(eventCaptor.capture());
        assertThat(eventCaptor.getValue().metadata()).isEmpty();
    }
}
