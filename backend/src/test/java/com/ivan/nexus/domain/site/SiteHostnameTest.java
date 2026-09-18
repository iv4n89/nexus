package com.ivan.nexus.domain.site;

import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SiteHostnameTest {

    @Test
    void normalizesCaseAndTrailingDot() {
        assertThat(SiteHostname.normalizeAndValidate("App.Example.COM.")).isEqualTo("app.example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            " ",
            "-bad.example.com",
            "bad-.example.com",
            "bad_label.example.com",
            "has space.example.com",
            ".leading.dot",
            "double..dot.com"
    })
    void rejectsInvalidHostnames(String hostname) {
        assertThatThrownBy(() -> SiteHostname.normalizeAndValidate(hostname))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getCode())
                        .isEqualTo(NexusErrorCode.DOMAIN_HOSTNAME_INVALID));
    }

    @Test
    void acceptsSimpleDnsLabels() {
        assertThat(SiteHostname.normalizeAndValidate("api-1.lab.local")).isEqualTo("api-1.lab.local");
    }
}
