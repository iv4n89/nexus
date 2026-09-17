package com.ivan.nexus.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpTest {

    @Test
    void usesForwardedForWhenPeerIsCaddyOnAPrivateNetwork() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.4");
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");
        assertThat(ClientIp.resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void ignoresForwardedForWhenPeerIsNotPrivate() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.9");
        request.addHeader("X-Forwarded-For", "203.0.113.10");
        assertThat(ClientIp.resolve(request)).isEqualTo("198.51.100.9");
    }
}
