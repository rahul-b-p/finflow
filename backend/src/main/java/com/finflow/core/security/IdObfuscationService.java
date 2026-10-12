package com.finflow.core.security;

import com.finflow.core.exception.IdDecryptionException;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * Service for obfuscating and de-obfuscating internal UUIDs into session-scoped opaque tokens.
 * Complies with Constitution Principle I: Zero raw database identifiers exposed over the wire.
 * Format: id_v1_<Base64Url(iv + ciphertext + authTag)>
 */
@Service
public class IdObfuscationService {

    public static final String PREFIX = "id_v1_";
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12; // 96 bits
    private static final int TAG_LENGTH_BITS = 128; // 16 bytes
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Generates a cryptographically strong 256-bit session encryption key.
     */
    public String generateSessionKey() {
        byte[] keyBytes = new byte[32];
        SECURE_RANDOM.nextBytes(keyBytes);
        return Base64.getEncoder().encodeToString(keyBytes);
    }

    /**
     * Obfuscates a UUID into an ephemeral token using the session's AES key.
     */
    public String obfuscate(UUID id, String base64SessionKey) {
        if (id == null) {
            return null;
        }
        if (base64SessionKey == null || base64SessionKey.isBlank()) {
            throw new IdDecryptionException("Session key must not be null or blank");
        }

        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64SessionKey);
            SecretKey secretKey = new SecretKeySpec(keyBytes, ALGORITHM);

            byte[] iv = new byte[IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] plaintext = id.toString().getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext = cipher.doFinal(plaintext);

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);

            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
            return PREFIX + encoded;
        } catch (Exception e) {
            throw new IdDecryptionException("Failed to obfuscate entity identifier", e);
        }
    }

    /**
     * De-obfuscates an opaque token back into the underlying UUID using the session key.
     * Throws IdDecryptionException (HTTP 449) if invalid, expired, or tampered.
     */
    public UUID deobfuscate(String token, String base64SessionKey) {
        if (token == null || !token.startsWith(PREFIX)) {
            throw new IdDecryptionException("Invalid identifier token prefix: expected " + PREFIX);
        }
        if (base64SessionKey == null || base64SessionKey.isBlank()) {
            throw new IdDecryptionException("Session key must not be null or blank");
        }

        try {
            String payload = token.substring(PREFIX.length());
            byte[] decoded = Base64.getUrlDecoder().decode(payload);

            if (decoded.length < IV_LENGTH_BYTES + (TAG_LENGTH_BITS / 8)) {
                throw new IdDecryptionException("Identifier token payload too short");
            }

            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);

            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            byte[] keyBytes = Base64.getDecoder().decode(base64SessionKey);
            SecretKey secretKey = new SecretKeySpec(keyBytes, ALGORITHM);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] plaintext = cipher.doFinal(ciphertext);
            String rawUuid = new String(plaintext, StandardCharsets.UTF_8);
            return UUID.fromString(rawUuid);
        } catch (IdDecryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new IdDecryptionException("Failed to decrypt obfuscated ID token: verification failed or tampered payload", e);
        }
    }
}
