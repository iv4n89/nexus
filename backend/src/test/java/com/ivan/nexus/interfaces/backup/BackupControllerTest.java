package com.ivan.nexus.interfaces.backup;

import com.ivan.nexus.application.backup.GetBackupPolicy;
import com.ivan.nexus.application.backup.ListBackups;
import com.ivan.nexus.application.backup.RestoreBackup;
import com.ivan.nexus.application.backup.UpsertBackupPolicy;
import com.ivan.nexus.domain.backup.Backup;
import com.ivan.nexus.domain.backup.BackupKind;
import com.ivan.nexus.domain.backup.BackupPolicy;
import com.ivan.nexus.domain.backup.BackupStatus;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.infrastructure.security.SecurityConfig;
import com.ivan.nexus.interfaces.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BackupController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class BackupControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GetBackupPolicy getBackupPolicy;

    @MockitoBean
    UpsertBackupPolicy upsertBackupPolicy;

    @MockitoBean
    ListBackups listBackups;

    @MockitoBean
    RestoreBackup restoreBackup;

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerGetsBackupPolicy() throws Exception {
        given(getBackupPolicy.execute("lab")).willReturn(new BackupPolicy("lab", 7, 4, 3, true, "0 3 * * *"));

        mockMvc.perform(get("/api/projects/lab/backup-policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value("lab"))
                .andExpect(jsonPath("$.dailyRetention").value(7))
                .andExpect(jsonPath("$.weeklyRetention").value(4))
                .andExpect(jsonPath("$.monthlyRetention").value(3))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.scheduleCron").value("0 3 * * *"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminPutsBackupPolicy() throws Exception {
        given(upsertBackupPolicy.execute(any())).willReturn(
                new BackupPolicy("lab", 14, 8, 6, true, "0 4 * * *"));

        mockMvc.perform(put("/api/projects/lab/backup-policy")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dailyRetention": 14,
                                  "weeklyRetention": 8,
                                  "monthlyRetention": 6,
                                  "enabled": true,
                                  "scheduleCron": "0 4 * * *"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyRetention").value(14))
                .andExpect(jsonPath("$.enabled").value(true));

        verify(upsertBackupPolicy).execute(new BackupPolicy("lab", 14, 8, 6, true, "0 4 * * *"));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerPutBackupPolicyForbidden() throws Exception {
        mockMvc.perform(put("/api/projects/lab/backup-policy")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dailyRetention": 7,
                                  "weeklyRetention": 4,
                                  "monthlyRetention": 3,
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isForbidden());
        verify(upsertBackupPolicy, never()).execute(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void putRejectsInvalidRetention() throws Exception {
        given(upsertBackupPolicy.execute(any()))
                .willThrow(new DomainException(NexusErrorCode.OPERATION_NOT_ALLOWED, "dailyRetention must be >= 0"));

        mockMvc.perform(put("/api/projects/lab/backup-policy")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dailyRetention": -1,
                                  "weeklyRetention": 4,
                                  "monthlyRetention": 3,
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("OPERATION_NOT_ALLOWED"));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerListsBackups() throws Exception {
        UUID id = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        given(listBackups.execute("lab")).willReturn(List.of(new Backup(
                id,
                "lab",
                BackupStatus.SUCCESS,
                BackupKind.SCHEDULED,
                Instant.parse("2026-09-18T03:00:00Z"),
                Instant.parse("2026-09-18T03:05:00Z"),
                null,
                "db dump",
                true,
                false)));

        mockMvc.perform(get("/api/projects/lab/backups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$[0].kind").value("SCHEDULED"))
                .andExpect(jsonPath("$[0].includesDb").value(true))
                .andExpect(jsonPath("$[0].includesVolumes").value(false));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminRestoresBackupWithConfirmation() throws Exception {
        UUID backupId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID safetyId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        Backup restored = new Backup(
                backupId, "lab", BackupStatus.SUCCESS, BackupKind.SCHEDULED,
                Instant.parse("2026-09-18T03:00:00Z"), Instant.parse("2026-09-18T03:05:00Z"),
                "file:///tmp/a.dump", "ok", true, false);
        Backup safety = new Backup(
                safetyId, "lab", BackupStatus.SUCCESS, BackupKind.SAFETY,
                Instant.parse("2026-09-18T12:00:00Z"), Instant.parse("2026-09-18T12:01:00Z"),
                "file:///tmp/s.dump", "safety", true, false);
        given(restoreBackup.execute(eq("lab"), eq(backupId), eq(true), eq("user"), any()))
                .willReturn(new RestoreBackup.RestoreResult(restored, safety, true));

        mockMvc.perform(post("/api/projects/lab/backups/{backupId}/restore", backupId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backupId").value(backupId.toString()))
                .andExpect(jsonPath("$.safetyBackupId").value(safetyId.toString()))
                .andExpect(jsonPath("$.healthy").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void restoreWithoutConfirmReturnsConflict() throws Exception {
        UUID backupId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        given(restoreBackup.execute(eq("lab"), eq(backupId), eq(false), eq("user"), any()))
                .willThrow(new DomainException(NexusErrorCode.CONFIRMATION_REQUIRED, "Confirmation required"));

        mockMvc.perform(post("/api/projects/lab/backups/{backupId}/restore", backupId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFIRMATION_REQUIRED"));
    }
}
