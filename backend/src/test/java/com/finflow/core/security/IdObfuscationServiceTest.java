package com.finflow.core.security;

import com.finflow.core.exception.IdDecryptionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdObfuscationServiceTest {

    private IdObfuscationService idObfuscationService;
    private String sessionKey;

    @BeforeEach
    void setUp() {
        idObfuscationService = new IdObfuscationService();
        sessionKey = idObfuscationService.generateSessionKey();
    }

    @Test
    @DisplayName("Successfully obfuscates and de-obfuscates a UUID deterministically")
    void shouldObfuscateAndDeobfuscateUuid() {
        UUID originalId = UUID.randomUUID();

        String token = idObfuscationService.obfuscate(originalId, sessionKey);

        assertThat(token).isNotNull();
        assertThat(token).startsWith("id_v1_");
        assertThat(token).doesNotContain(originalId.toString());

        UUID decryptedId = idObfuscationService.deobfuscate(token, sessionKey);
        assertThat(decryptedId).isEqualTo(originalId);
    }

    @Test
    @DisplayName("Throws IdDecryptionException when token prefix is invalid or missing")
    void shouldThrowWhenPrefixIsInvalid() {
        assertThatThrownBy(() -> idObfuscationService.deobfuscate("invalid_token_123", sessionKey))
                .isInstanceOf(IdDecryptionException.class)
                .hasMessageContaining("Invalid identifier token prefix");
    }

    @Test
    @DisplayName("Throws IdDecryptionException when decrypted with a different session key")
    void shouldThrowWhenSessionKeyMismatch() {
        UUID originalId = UUID.randomUUID();
        String token = idObfuscationService.obfuscate(originalId, sessionKey);

        String otherSessionKey = idObfuscationService.generateSessionKey();

        assertThatThrownBy(() -> idObfuscationService.deobfuscate(token, otherSessionKey))
                .isInstanceOf(IdDecryptionException.class);
    }

    @Test
    @DisplayName("Throws IdDecryptionException when token payload has been tampered with")
    void shouldThrowWhenPayloadIsTampered() {
        UUID originalId = UUID.randomUUID();
        String token = idObfuscationService.obfuscate(originalId, sessionKey);

        // Tamper with one character
        char lastChar = token.charAt(token.length() - 1);
        char tamperedChar = (lastChar == 'A') ? 'B' : 'A';
        String tamperedToken = token.substring(0, token.length() - 1) + tamperedChar;

        assertThatThrownBy(() -> idObfuscationService.deobfuscate(tamperedToken, sessionKey))
                .isInstanceOf(IdDecryptionException.class);
    }
}
