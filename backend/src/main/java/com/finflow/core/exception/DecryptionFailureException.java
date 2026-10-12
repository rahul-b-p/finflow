package com.finflow.core.exception;

import org.springframework.http.HttpStatus;

public class DecryptionFailureException extends BaseException {

    public DecryptionFailureException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, "CRYPTO_DECRYPTION_FAILED");
    }

    public DecryptionFailureException(String message, Throwable cause) {
        super(message, cause, HttpStatus.INTERNAL_SERVER_ERROR, "CRYPTO_DECRYPTION_FAILED");
    }
}
