package com.ivan.nexus.domain.site;

import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;

public final class SiteHostname {
    private static final int MAX_HOSTNAME_LENGTH = 253;
    private static final int MAX_LABEL_LENGTH = 63;

    private SiteHostname() {
    }

    public static String normalizeAndValidate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DomainException(NexusErrorCode.DOMAIN_HOSTNAME_INVALID, "Hostname is required");
        }
        String hostname = raw.trim().toLowerCase();
        if (hostname.endsWith(".")) {
            hostname = hostname.substring(0, hostname.length() - 1);
        }
        if (hostname.isEmpty() || hostname.length() > MAX_HOSTNAME_LENGTH) {
            throw new DomainException(NexusErrorCode.DOMAIN_HOSTNAME_INVALID, "Hostname length is invalid");
        }
        String[] labels = hostname.split("\\.", -1);
        if (labels.length == 0) {
            throw new DomainException(NexusErrorCode.DOMAIN_HOSTNAME_INVALID, "Hostname has no labels");
        }
        for (String label : labels) {
            if (!isValidLabel(label)) {
                throw new DomainException(
                        NexusErrorCode.DOMAIN_HOSTNAME_INVALID,
                        "Hostname label is invalid: " + label);
            }
        }
        return hostname;
    }

    private static boolean isValidLabel(String label) {
        if (label.isEmpty() || label.length() > MAX_LABEL_LENGTH) {
            return false;
        }
        if (label.charAt(0) == '-' || label.charAt(label.length() - 1) == '-') {
            return false;
        }
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
