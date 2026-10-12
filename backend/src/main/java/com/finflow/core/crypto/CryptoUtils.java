package com.finflow.core.crypto;

import com.finflow.core.exception.DecryptionFailureException;
import com.finflow.core.exception.InvalidConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public final class CryptoUtils {

    private static final Logger log = LoggerFactory.getLogger(CryptoUtils.class);

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12; // 96 bits
    private static final int TAG_LENGTH_BITS = 128; // 16 bytes auth tag

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static volatile SecretKey secretKey;

    static {
        String envKey = System.getenv("PII_ENCRYPTION_KEY");
        if (envKey == null || envKey.isBlank()) {
            envKey = System.getProperty("finflow.security.pii-encryption-key");
        }
        if (envKey != null && !envKey.isBlank()) {
            try {
                init(envKey);
            } catch (Exception e) {
                log.warn("Failed to initialize CryptoUtils with environment key: {}", e.getMessage());
            }
        }
    }

    private CryptoUtils() {
    }

    public static synchronized void init(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new InvalidConfigurationException("Encryption key must not be null or empty");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Key.trim());
            if (decoded.length != 32) {
                throw new InvalidConfigurationException("AES-256 requires a 32-byte key, provided: " + decoded.length);
            }
            secretKey = new SecretKeySpec(decoded, ALGORITHM);
        } catch (IllegalArgumentException e) {
            throw new InvalidConfigurationException("Invalid Base64 format for PII encryption key", e);
        }
    }

    public static synchronized void init(byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length != 32) {
            throw new InvalidConfigurationException("AES-256 requires a 32-byte key");
        }
        secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    public static String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        ensureKeyInitialized();
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext = cipher.doFinal(plaintextBytes);

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            throw new DecryptionFailureException("Failed to encrypt data", e);
        }
    }

    public static String decrypt(String ciphertextBase64) {
        if (ciphertextBase64 == null) {
            return null;
        }
        ensureKeyInitialized();
        try {
            byte[] decoded = Base64.getDecoder().decode(ciphertextBase64);
            if (decoded.length < IV_LENGTH_BYTES + (TAG_LENGTH_BITS / 8)) {
                throw new DecryptionFailureException("Invalid ciphertext length: payload too short");
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            byteBuffer.get(iv);

            byte[] ciphertext = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] plainBytes = cipher.doFinal(ciphertext);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new DecryptionFailureException("Failed to decrypt data: authentication tag verification failed or corrupted ciphertext", e);
        }
    }

    private static void ensureKeyInitialized() {
        if (secretKey == null) {
            // Default 32-byte key for local/dev/test fallback
            String fallback = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
            init(fallback);
        }
    }
}
