package com.ivan.nexus.application.log;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchLogsTest {
    private final SearchLogs searchLogs = new SearchLogs();

    @Test
    void queryTimeoutMatchesMixedCaseLines() {
        List<String> lines = List.of(
                "Connection TIMEOUT after 5s",
                "request completed",
                "timed out waiting for timeout");

        assertThat(searchLogs.execute(lines, "timeout", null))
                .containsExactly("Connection TIMEOUT after 5s", "timed out waiting for timeout");
    }

    @Test
    void queryDoesNotMatchUnrelatedLines() {
        List<String> lines = List.of("ready", "healthy", "listening on 8080");

        assertThat(searchLogs.execute(lines, "timeout", null)).isEmpty();
    }

    @Test
    void levelErrorKeepsLinesContainingErrorIncludingMixedCase() {
        List<String> lines = List.of(
                "INFO started",
                "WARN retry",
                "ERROR boom",
                "error: connection refused",
                "ok");

        assertThat(searchLogs.execute(lines, null, "ERROR"))
                .containsExactly("ERROR boom", "error: connection refused");
    }

    @Test
    void levelErrorKeepsExceptionAndFatalWithoutTheWordError() {
        List<String> lines = List.of(
                "INFO started successfully",
                "java.net.ConnectException: Connection refused",
                "FATAL panic in worker",
                "Traceback (most recent call last):",
                "WARN retry later");

        assertThat(searchLogs.execute(lines, null, "ERROR")).containsExactly(
                "java.net.ConnectException: Connection refused",
                "FATAL panic in worker",
                "Traceback (most recent call last):");
    }

    @Test
    void levelErrorDoesNotTreatInfoTimeoutAsError() {
        List<String> lines = List.of("INFO timeout ignored", "ERROR timeout on db");

        assertThat(searchLogs.execute(lines, null, "ERROR")).containsExactly("ERROR timeout on db");
    }

    @Test
    void levelWarnKeepsWarningWithoutRequiringError() {
        List<String> lines = List.of(
                "INFO started",
                "WARN retry",
                "WARNING deprecated api",
                "ERROR boom");

        assertThat(searchLogs.execute(lines, null, "WARN"))
                .containsExactly("WARN retry", "WARNING deprecated api");
    }

    @Test
    void allNullOrBlankLevelDoesNotFilter() {
        List<String> lines = List.of("INFO started", "ERROR boom");

        assertThat(searchLogs.execute(lines, null, "ALL")).containsExactlyElementsOf(lines);
        assertThat(searchLogs.execute(lines, null, null)).containsExactlyElementsOf(lines);
        assertThat(searchLogs.execute(lines, null, "")).containsExactlyElementsOf(lines);
        assertThat(searchLogs.execute(lines, null, "  ")).containsExactlyElementsOf(lines);
    }

    @Test
    void blankQueryDoesNotFilter() {
        List<String> lines = List.of("INFO started", "ERROR boom");

        assertThat(searchLogs.execute(lines, null, null)).containsExactlyElementsOf(lines);
        assertThat(searchLogs.execute(lines, "", null)).containsExactlyElementsOf(lines);
        assertThat(searchLogs.execute(lines, "  ", null)).containsExactlyElementsOf(lines);
    }

    @Test
    void queryAndLevelApplyTogether() {
        List<String> lines = List.of(
                "ERROR timeout on db",
                "INFO timeout ignored",
                "ERROR unrelated",
                "warn timeout");

        assertThat(searchLogs.execute(lines, "timeout", "ERROR"))
                .containsExactly("ERROR timeout on db");
    }
}
