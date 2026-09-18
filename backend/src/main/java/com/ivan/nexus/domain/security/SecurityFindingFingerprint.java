package com.ivan.nexus.domain.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Stable fingerprint from source + package + title + installedVersion.
 */
public final class SecurityFindingFingerprint {
    private SecurityFindingFingerprint() {
    }

    public static String compute(String source, String packageName, String title, String installedVersion) {
        String material = nullToEmpty(source)
                + "|" + nullToEmpty(packageName)
                + "|" + nullToEmpty(title)
                + "|" + nullToEmpty(installedVersion);
        return sha256Hex(material);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
