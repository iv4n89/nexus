package com.ivan.nexus.application.security;

import com.ivan.nexus.application.manifest.ManifestCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScheduledSecurityScanTest {

    @Mock
    ManifestCatalog manifests;
    @Mock
    RunSecurityScan runSecurityScan;
    @InjectMocks
    ScheduledSecurityScan scheduled;

    @Test
    void scansEachDiscoveredProject() {
        given(manifests.discoverProjectIds()).willReturn(Set.of("lab", "demo"));

        scheduled.execute();

        verify(runSecurityScan).execute("lab");
        verify(runSecurityScan).execute("demo");
    }

    @Test
    void continuesWhenOneProjectFails() {
        given(manifests.discoverProjectIds()).willReturn(Set.of("lab", "demo"));
        org.mockito.Mockito.lenient()
                .doThrow(new IllegalStateException("boom"))
                .when(runSecurityScan)
                .execute("lab");

        scheduled.execute();

        verify(runSecurityScan).execute("lab");
        verify(runSecurityScan).execute("demo");
    }

    @Test
    void noProjectsMeansNoScans() {
        given(manifests.discoverProjectIds()).willReturn(Set.of());

        scheduled.execute();

        verify(runSecurityScan, never()).execute(org.mockito.ArgumentMatchers.anyString());
    }
}
