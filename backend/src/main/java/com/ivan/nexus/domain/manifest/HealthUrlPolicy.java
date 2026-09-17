package com.ivan.nexus.domain.manifest;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * Fail-closed allowlist for HTTP health checks. Loopback and RFC1918 (Docker
 * bridges) are allowed so project checks on this VPS keep working. Link-local
 * metadata, non-http schemes, and the public internet are not.
 */
public final class HealthUrlPolicy {
    private static final Set<String> DENIED_HOSTS = Set.of(
            "metadata",
            "metadata.google.internal",
            "metadata.goog",
            "instance-data");

    private HealthUrlPolicy() {}

    public static boolean allowed(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException ex) {
            return false;
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            return false;
        }
        String normalized = scheme.toLowerCase(Locale.ROOT);
        if (!normalized.equals("http") && !normalized.equals("https")) {
            return false;
        }
        if (uri.getUserInfo() != null) {
            return false;
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return false;
        }
        String lowerHost = host.toLowerCase(Locale.ROOT);
        if (DENIED_HOSTS.contains(lowerHost)) {
            return false;
        }
        if (lowerHost.equals("localhost") || lowerHost.endsWith(".localhost")) {
            return true;
        }
        InetAddress address = literalAddress(host);
        if (address == null) {
            return false;
        }
        return allowedAddress(address);
    }

    private static boolean allowedAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isMulticastAddress() || address.isLinkLocalAddress()) {
            return false;
        }
        return address.isLoopbackAddress() || address.isSiteLocalAddress();
    }

    private static InetAddress literalAddress(String host) {
        boolean ipv4 = host.chars().allMatch(ch -> ch == '.' || (ch >= '0' && ch <= '9'));
        boolean ipv6 = host.indexOf(':') >= 0;
        if (!ipv4 && !ipv6) {
            return null;
        }
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException ex) {
            return null;
        }
    }
}
