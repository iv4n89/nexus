package com.ivan.nexus.infrastructure.github;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Verifies GitHub webhook HMAC SHA-256 signatures ({@code X-Hub-Signature-256}).
 */
public final class GitHubWebhookSignature {
    private static final String PREFIX = "sha256=";
    private static final HexFormat HEX = HexFormat.of();

    private GitHubWebhookSignature() {
    }

    public static boolean isValid(String secret, byte[] body, String signatureHeader) {
        if (secret == null || secret.isBlank() || body == null || signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        String expected = PREFIX + hmacSha256Hex(secret, body);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signatureHeader.trim().getBytes(StandardCharsets.UTF_8));
    }

    public static String sign(String secret, byte[] body) {
        return PREFIX + hmacSha256Hex(secret, body);
    }

    private static String hmacSha256Hex(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HEX.formatHex(mac.doFinal(body));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Unable to compute webhook HMAC", e);
        }
    }
}
