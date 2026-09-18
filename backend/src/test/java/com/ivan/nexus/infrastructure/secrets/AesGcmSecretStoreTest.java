package com.ivan.nexus.infrastructure.secrets;

import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmSecretStoreTest {

    @Test
    void roundTripsWithBase64Key() {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        String key = Base64.getEncoder().encodeToString(raw);
        AesGcmSecretStore store = new AesGcmSecretStore(key);

        String ciphertext = store.encrypt("gho_token_value");
        assertThat(ciphertext).isNotEqualTo("gho_token_value");
        assertThat(store.decrypt(ciphertext)).isEqualTo("gho_token_value");
    }

    @Test
    void derivesKeyFromPassphraseWhenNotBase64AesKey() {
        AesGcmSecretStore store = new AesGcmSecretStore("dev-passphrase-not-base64");
        assertThat(store.decrypt(store.encrypt("secret"))).isEqualTo("secret");
    }

    @Test
    void resolveKeyBytesAcceptsExact32ByteBase64() {
        byte[] raw = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        assertThat(raw).hasSize(32);
        byte[] resolved = AesGcmSecretStore.resolveKeyBytes(Base64.getEncoder().encodeToString(raw));
        assertThat(resolved).isEqualTo(raw);
    }

    @Test
    void missingKeyFailsOnEncrypt() {
        AesGcmSecretStore store = new AesGcmSecretStore("  ");
        assertThatThrownBy(() -> store.encrypt("x"))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getCode())
                        .isEqualTo(NexusErrorCode.GITHUB_NOT_CONFIGURED));
    }

    @Test
    void ciphertextDiffersAcrossEncrypts() {
        AesGcmSecretStore store = new AesGcmSecretStore("passphrase");
        assertThat(store.encrypt("same")).isNotEqualTo(store.encrypt("same"));
    }
}
