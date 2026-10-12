package com.finflow.core.security;

import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * Service implementing RFC 6238 TOTP (Time-Based One-Time Password) algorithm.
 * Standard configuration: HMAC-SHA1, 30-second time-step, 6 digits, ±1 window skew tolerance.
 */
@Service
public class TotpService {

    private static final int TIME_STEP_SECONDS = 30;
    private static final int CODE_DIGITS = 6;
    private static final int MODULUS = 1_000_000; // 10^6
    private static final String HMAC_ALGO = "HmacSHA1";
    private static final int SECRET_BYTE_LENGTH = 20; // 160 bits

    private final Base32 base32 = new Base32();
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a cryptographically secure random Base32 shared secret.
     */
    public String generateSecret() {
        byte[] buffer = new byte[SECRET_BYTE_LENGTH];
        secureRandom.nextBytes(buffer);
        return base32.encodeToString(buffer).replace("=", "");
    }

    /**
     * Generates an otpauth:// key URI for QR code pairing with standard authenticator apps.
     */
    public String generateKeyUri(String email, String secret, String issuer) {
        String encodedIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8).replace("+", "%20");
        return String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s",
                encodedIssuer,
                encodedEmail,
                secret,
                encodedIssuer);
    }

    /**
     * Generates the 6-digit TOTP code for the given secret at a specific Unix timestamp (seconds).
     */
    public String generateCode(String secret, long timestampSeconds) {
        long timeStep = timestampSeconds / TIME_STEP_SECONDS;
        return generateCodeForStep(secret, timeStep);
    }

    /**
     * Validates a candidate 6-digit code against the secret for the current system time,
     * allowing for clock skew of ±1 step (±30 seconds).
     */
    public boolean verifyCode(String secret, String candidateCode) {
        long currentTimestamp = System.currentTimeMillis() / 1000L;
        return verifyCode(secret, candidateCode, currentTimestamp);
    }

    /**
     * Validates a candidate 6-digit code against the secret at a specific timestamp,
     * checking steps: current-1, current, and current+1.
     */
    public boolean verifyCode(String secret, String candidateCode, long timestampSeconds) {
        if (candidateCode == null || candidateCode.length() != CODE_DIGITS || secret == null) {
            return false;
        }

        long currentStep = timestampSeconds / TIME_STEP_SECONDS;

        // Tolerant window: -1, 0, +1
        for (long step = currentStep - 1; step <= currentStep + 1; step++) {
            String expected = generateCodeForStep(secret, step);
            if (expected.equals(candidateCode)) {
                return true;
            }
        }
        return false;
    }

    private String generateCodeForStep(String secret, long timeStep) {
        try {
            byte[] keyBytes = base32.decode(secret.toUpperCase());
            byte[] data = ByteBuffer.allocate(8).putLong(timeStep).array();

            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(keyBytes, HMAC_ALGO));
            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0x0F;
            int truncatedHash = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int code = truncatedHash % MODULUS;
            return String.format("%06d", code);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA1 for TOTP", e);
        }
    }
}
