package com.ivan.nexus.application.secrets;

/**
 * Opaque encryption for secrets at rest. Plaintext must never be persisted.
 */
public interface SecretStore {
    String encrypt(String plaintext);

    String decrypt(String ciphertext);
}
