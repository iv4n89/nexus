package com.ivan.nexus.domain.deployment;

import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeploymentTransitionsTest {

    @Test
    void pendingToRunningToSuccessIsAllowed() {
        assertThat(DeploymentTransitions.next(DeploymentStatus.PENDING, DeploymentStatus.RUNNING))
                .isEqualTo(DeploymentStatus.RUNNING);
        assertThat(DeploymentTransitions.next(DeploymentStatus.RUNNING, DeploymentStatus.SUCCESS))
                .isEqualTo(DeploymentStatus.SUCCESS);
    }

    @Test
    void successToRunningThrowsInvalidTransition() {
        assertInvalidTransition(DeploymentStatus.SUCCESS, DeploymentStatus.RUNNING);
    }

    @Test
    void pendingToFailedThrowsInvalidTransition() {
        assertInvalidTransition(DeploymentStatus.PENDING, DeploymentStatus.FAILED);
    }

    @Test
    void pendingToCancelledIsAllowed() {
        assertThat(DeploymentTransitions.next(DeploymentStatus.PENDING, DeploymentStatus.CANCELLED))
                .isEqualTo(DeploymentStatus.CANCELLED);
    }

    @Test
    void runningToFailedIsAllowed() {
        assertThat(DeploymentTransitions.next(DeploymentStatus.RUNNING, DeploymentStatus.FAILED))
                .isEqualTo(DeploymentStatus.FAILED);
    }

    @Test
    void runningToCancelledIsAllowed() {
        assertThat(DeploymentTransitions.next(DeploymentStatus.RUNNING, DeploymentStatus.CANCELLED))
                .isEqualTo(DeploymentStatus.CANCELLED);
    }

    @ParameterizedTest
    @EnumSource(DeploymentStatus.class)
    void failedToAnythingThrowsInvalidTransition(DeploymentStatus to) {
        assertInvalidTransition(DeploymentStatus.FAILED, to);
    }

    private static void assertInvalidTransition(DeploymentStatus from, DeploymentStatus to) {
        assertThatThrownBy(() -> DeploymentTransitions.next(from, to))
                .isInstanceOf(DomainException.class)
                .hasMessage("Invalid deployment transition")
                .satisfies(ex -> assertThat(((DomainException) ex).getCode())
                        .isEqualTo(NexusErrorCode.INVALID_TRANSITION));
    }
}
