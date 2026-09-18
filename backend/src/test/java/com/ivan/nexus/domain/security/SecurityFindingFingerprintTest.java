package com.ivan.nexus.domain.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityFindingFingerprintTest {

    @Test
    void sameInputsProduceStableFingerprint() {
        String a = SecurityFindingFingerprint.compute("trivy", "openssl", "CVE-2024-1", "1.0.0");
        String b = SecurityFindingFingerprint.compute("trivy", "openssl", "CVE-2024-1", "1.0.0");
        assertThat(a).isEqualTo(b).hasSize(64);
    }

    @Test
    void differentFieldsChangeFingerprint() {
        String base = SecurityFindingFingerprint.compute("trivy", "openssl", "CVE-2024-1", "1.0.0");
        assertThat(SecurityFindingFingerprint.compute("osv", "openssl", "CVE-2024-1", "1.0.0"))
                .isNotEqualTo(base);
        assertThat(SecurityFindingFingerprint.compute("trivy", "curl", "CVE-2024-1", "1.0.0"))
                .isNotEqualTo(base);
        assertThat(SecurityFindingFingerprint.compute("trivy", "openssl", "CVE-2024-2", "1.0.0"))
                .isNotEqualTo(base);
        assertThat(SecurityFindingFingerprint.compute("trivy", "openssl", "CVE-2024-1", "1.0.1"))
                .isNotEqualTo(base);
    }

    @Test
    void nullsAreTreatedAsEmpty() {
        String withNulls = SecurityFindingFingerprint.compute(null, null, "title", null);
        String withEmpty = SecurityFindingFingerprint.compute("", "", "title", "");
        assertThat(withNulls).isEqualTo(withEmpty);
    }
}
