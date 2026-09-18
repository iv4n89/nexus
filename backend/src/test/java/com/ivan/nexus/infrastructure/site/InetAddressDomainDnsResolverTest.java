package com.ivan.nexus.infrastructure.site;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InetAddressDomainDnsResolverTest {

    private final InetAddressDomainDnsResolver resolver = new InetAddressDomainDnsResolver();

    @Test
    void resolvesLocalhost() {
        assertThat(resolver.resolves("localhost")).isTrue();
    }

    @Test
    void blankHostnameDoesNotResolve() {
        assertThat(resolver.resolves("")).isFalse();
        assertThat(resolver.resolves("   ")).isFalse();
        assertThat(resolver.resolves(null)).isFalse();
    }

    @Test
    void unknownHostDoesNotResolve() {
        assertThat(resolver.resolves("no-such-host.invalid.nexus-test")).isFalse();
    }
}
