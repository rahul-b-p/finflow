package com.finflow.core.exception;

import org.springframework.http.HttpStatusCode;

/**
 * Thrown when an obfuscated entity ID token cannot be decrypted or is malformed.
 * Responds with HTTP 449 Retry With, prompting client to re-authenticate or re-fetch valid tokens.
 */
public class IdDecryptionException extends BaseException {

    public IdDecryptionException(String message) {
        super(message, HttpStatusCode.valueOf(449), "ID_DECRYPTION_FAILED");
    }

    public IdDecryptionException(String message, Throwable cause) {
        super(message, cause, HttpStatusCode.valueOf(449), "ID_DECRYPTION_FAILED");
    }
}
