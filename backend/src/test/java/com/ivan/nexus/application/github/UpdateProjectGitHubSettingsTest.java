package com.ivan.nexus.application.github;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.deployment.ManagedProjectStore;
import com.ivan.nexus.application.user.UserDirectory;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateProjectGitHubSettingsTest {

    @Mock
    ManagedProjectStore projects;
    @Mock
    UserDirectory users;
    @Mock
    RecordAudit recordAudit;
    @Mock
    RecordActivity recordActivity;
    @InjectMocks
    UpdateProjectGitHubSettings useCase;

    @Test
    void enablesAutodeployAndRecordsAudit() {
        when(projects.findGitHubLink("lab")).thenReturn(Optional.of(
                new ProjectGitHubLink("lab", "octo", "lab-repo", "main", false, null)));
        UUID userId = UUID.randomUUID();
        when(users.findIdByUsername("admin")).thenReturn(Optional.of(userId));

        ProjectGitHubLink updated = useCase.execute("lab", true, "admin", "127.0.0.1");

        assertThat(updated.autodeployEnabled()).isTrue();
        verify(projects).setAutodeployEnabled("lab", true);
        verify(recordAudit).execute(
                eq(userId),
                eq(AuditAction.PROJECT_GITHUB_UPDATE),
                eq("lab"),
                isNull(),
                eq("127.0.0.1"),
                anyMap());
        verify(recordActivity).execute(
                eq(ActivityType.CONFIG_CHANGED),
                eq("lab"),
                isNull(),
                eq("GitHub autodeploy enabled"),
                anyMap());
    }

    @Test
    void throwsWhenProjectNotLinked() {
        when(projects.findGitHubLink("lab")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("lab", true, "admin", "127.0.0.1"))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(NexusErrorCode.PROJECT_GITHUB_NOT_LINKED);
    }
}
