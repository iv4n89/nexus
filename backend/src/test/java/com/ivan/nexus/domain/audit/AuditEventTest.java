package com.ivan.nexus.domain.audit;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditEventTest {

    @Test
    void defensivelyCopiesMetadataAndPreservesNullValues() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("nullable", null);
        AuditEvent event = new AuditEvent(
                UUID.randomUUID(),
                null,
                AuditAction.LOGIN,
                null,
                null,
                null,
                metadata,
                Instant.now());

        metadata.put("later", "mutation");

        assertThat(event.metadata()).containsEntry("nullable", null).doesNotContainKey("later");
        assertThatThrownBy(() -> event.metadata().put("other", "mutation"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
