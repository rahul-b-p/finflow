package com.finflow.core.crypto;

import com.finflow.core.exception.DecryptionFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmCryptoConverterTest {

    private static final String TEST_KEY_BASE64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private AesGcmCryptoConverter converter;

    @BeforeEach
    void setUp() {
        CryptoUtils.init(TEST_KEY_BASE64);
        converter = new AesGcmCryptoConverter();
    }

    @Test
    @DisplayName("Encrypt and decrypt round trip succeeds")
    void shouldEncryptAndDecryptSuccessfully() {
        String original = "user@finflow.internal";
        String encrypted = converter.convertToDatabaseColumn(original);

        assertThat(encrypted).isNotNull();
        assertThat(encrypted).isNotEqualTo(original);

        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    @DisplayName("Null values pass through untouched")
    void shouldHandleNullValues() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    @DisplayName("Tampered ciphertext throws DecryptionFailureException")
    void shouldDetectTampering() {
        String encrypted = converter.convertToDatabaseColumn("sensitive-payload");
        byte[] raw = Base64.getDecoder().decode(encrypted);
        // Tamper with last byte (auth tag)
        raw[raw.length - 1] = (byte) (raw[raw.length - 1] ^ 0xFF);
        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
                .isInstanceOf(DecryptionFailureException.class);
    }

    @Test
    @DisplayName("Invalid short ciphertext throws DecryptionFailureException")
    void shouldRejectInvalidCiphertext() {
        String invalid = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        assertThatThrownBy(() -> converter.convertToEntityAttribute(invalid))
                .isInstanceOf(DecryptionFailureException.class);
    }
}
