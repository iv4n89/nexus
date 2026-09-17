package com.ivan.nexus.infrastructure.persistence.alert;

import com.ivan.nexus.domain.alert.AlertType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "alert_rules")
public class AlertRuleEntity {

    @Id
    private UUID id;

    @Column(name = "project_id", length = 64)
    private String projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AlertType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "threshold_json", nullable = false)
    private Map<String, Object> thresholdJson;

    @Column(nullable = false)
    private boolean enabled;

    protected AlertRuleEntity() {
    }

    public AlertRuleEntity(
            UUID id,
            String projectId,
            AlertType type,
            Map<String, Object> thresholdJson,
            boolean enabled) {
        this.id = id;
        this.projectId = projectId;
        this.type = type;
        this.thresholdJson = thresholdJson == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(thresholdJson);
        this.enabled = enabled;
    }

    public UUID getId() {
        return id;
    }

    public String getProjectId() {
        return projectId;
    }

    public AlertType getType() {
        return type;
    }

    public Map<String, Object> getThresholdJson() {
        return thresholdJson;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
