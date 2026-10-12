package com.finflow.core.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {

    private TotpService totpService;

    @BeforeEach
    void setUp() {
        totpService = new TotpService();
    }

    @Test
    @DisplayName("Generates non-empty, distinct Base32 secrets")
    void shouldGenerateValidSecrets() {
        String secret1 = totpService.generateSecret();
        String secret2 = totpService.generateSecret();

        assertThat(secret1).isNotBlank();
        assertThat(secret2).isNotBlank();
        assertThat(secret1).isNotEqualTo(secret2);
        assertThat(secret1).matches("^[A-Z2-7]+$");
    }

    @Test
    @DisplayName("Generates 6-digit TOTP codes conforming to RFC 6238")
    void shouldGenerateSixDigitCode() {
        String secret = "JBSWY3DPEHPK3PXP"; // Standard Base32 test secret
        long timestamp = 1700000000L;

        String code = totpService.generateCode(secret, timestamp);

        assertThat(code).hasSize(6);
        assertThat(code).matches("^[0-9]{6}$");
    }

    @Test
    @DisplayName("Verifies code at exact timestamp, step -1, and step +1 (±30s skew tolerance)")
    void shouldVerifyCodeWithClockSkewTolerance() {
        String secret = "JBSWY3DPEHPK3PXP";
        long currentTimestamp = 1700000000L; // Exactly at time step T

        String currentCode = totpService.generateCode(secret, currentTimestamp);

        // Exact match
        assertThat(totpService.verifyCode(secret, currentCode, currentTimestamp)).isTrue();

        // Skew: 25 seconds later (within same step)
        assertThat(totpService.verifyCode(secret, currentCode, currentTimestamp + 25L)).isTrue();

        // Skew: 30 seconds earlier (step - 1)
        assertThat(totpService.verifyCode(secret, currentCode, currentTimestamp + 30L)).isTrue();

        // Skew: 30 seconds later (step + 1)
        assertThat(totpService.verifyCode(secret, currentCode, currentTimestamp - 30L)).isTrue();

        // Beyond tolerance: 65 seconds earlier (step - 2)
        assertThat(totpService.verifyCode(secret, currentCode, currentTimestamp + 65L)).isFalse();

        // Beyond tolerance: 65 seconds later (step + 2)
        assertThat(totpService.verifyCode(secret, currentCode, currentTimestamp - 65L)).isFalse();
    }

    @Test
    @DisplayName("Rejects malformed, incorrect, or null codes")
    void shouldRejectInvalidCodes() {
        String secret = "JBSWY3DPEHPK3PXP";
        long timestamp = 1700000000L;

        assertThat(totpService.verifyCode(secret, "000000", timestamp)).isFalse();
        assertThat(totpService.verifyCode(secret, "12345", timestamp)).isFalse();
        assertThat(totpService.verifyCode(secret, "1234567", timestamp)).isFalse();
        assertThat(totpService.verifyCode(secret, null, timestamp)).isFalse();
        assertThat(totpService.verifyCode(null, "123456", timestamp)).isFalse();
    }

    @Test
    @DisplayName("Generates correct otpauth:// QR pairing URI")
    void shouldGenerateOtpAuthUri() {
        String secret = "JBSWY3DPEHPK3PXP";
        String email = "john.doe@finflow.internal";
        String issuer = "FinFlow";

        String uri = totpService.generateKeyUri(email, secret, issuer);

        assertThat(uri).startsWith("otpauth://totp/FinFlow:john.doe%40finflow.internal?");
        assertThat(uri).contains("secret=JBSWY3DPEHPK3PXP");
        assertThat(uri).contains("issuer=FinFlow");
    }
}
