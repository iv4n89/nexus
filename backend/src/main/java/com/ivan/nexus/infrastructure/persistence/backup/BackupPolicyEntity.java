package com.ivan.nexus.infrastructure.persistence.backup;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "backup_policies")
public class BackupPolicyEntity {

    @Id
    @Column(name = "project_id", length = 64)
    private String projectId;

    @Column(name = "daily_retention", nullable = false)
    private int dailyRetention;

    @Column(name = "weekly_retention", nullable = false)
    private int weeklyRetention;

    @Column(name = "monthly_retention", nullable = false)
    private int monthlyRetention;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "schedule_cron", length = 64)
    private String scheduleCron;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BackupPolicyEntity() {
    }

    public BackupPolicyEntity(
            String projectId,
            int dailyRetention,
            int weeklyRetention,
            int monthlyRetention,
            boolean enabled,
            String scheduleCron) {
        this.projectId = projectId;
        this.dailyRetention = dailyRetention;
        this.weeklyRetention = weeklyRetention;
        this.monthlyRetention = monthlyRetention;
        this.enabled = enabled;
        this.scheduleCron = scheduleCron;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    void apply(
            int dailyRetention,
            int weeklyRetention,
            int monthlyRetention,
            boolean enabled,
            String scheduleCron) {
        this.dailyRetention = dailyRetention;
        this.weeklyRetention = weeklyRetention;
        this.monthlyRetention = monthlyRetention;
        this.enabled = enabled;
        this.scheduleCron = scheduleCron;
    }

    public String getProjectId() {
        return projectId;
    }

    public int getDailyRetention() {
        return dailyRetention;
    }

    public int getWeeklyRetention() {
        return weeklyRetention;
    }

    public int getMonthlyRetention() {
        return monthlyRetention;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getScheduleCron() {
        return scheduleCron;
    }
}
