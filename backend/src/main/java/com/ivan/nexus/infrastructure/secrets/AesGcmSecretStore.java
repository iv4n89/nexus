package com.ivan.nexus.infrastructure.secrets;

import com.ivan.nexus.application.secrets.SecretStore;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM encryption. Key from base64-encoded 32-byte {@code NEXUS_SECRETS_KEY},
 * or SHA-256 derivation of a configured passphrase.
 */
public class AesGcmSecretStore implements SecretStore {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int AES_KEY_LENGTH = 32;

    private final SecretKey key;
    private final SecureRandom secureRandom;

    public AesGcmSecretStore(String configuredKey) {
        this(configuredKey, new SecureRandom());
    }

    AesGcmSecretStore(String configuredKey, SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
        if (configuredKey == null || configuredKey.isBlank()) {
            this.key = null;
        } else {
            this.key = new SecretKeySpec(resolveKeyBytes(configuredKey.trim()), "AES");
        }
    }

    static byte[] resolveKeyBytes(String configuredKey) {
        try {
            byte[] decoded = Base64.getDecoder().decode(configuredKey);
            if (decoded.length == AES_KEY_LENGTH) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // fall through to passphrase derivation
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(configuredKey.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to derive secrets key", ex);
        }
    }

    @Override
    public String encrypt(String plaintext) {
        requireKey();
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext must not be null");
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encrypt secret", ex);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        requireKey();
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new IllegalArgumentException("ciphertext must not be blank");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(ciphertext);
            if (payload.length <= GCM_IV_LENGTH) {
                throw new IllegalArgumentException("ciphertext too short");
            }
            byte[] iv = Arrays.copyOfRange(payload, 0, GCM_IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(payload, GCM_IV_LENGTH, payload.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(encrypted);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | DomainException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to decrypt secret", ex);
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new DomainException(
                    NexusErrorCode.GITHUB_NOT_CONFIGURED,
                    "NEXUS_SECRETS_KEY is required for secret encryption");
        }
    }
}
