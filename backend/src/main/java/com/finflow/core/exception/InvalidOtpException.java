package com.finflow.core.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an invalid or expired OTP / TOTP code is provided.
 */
public class InvalidOtpException extends BaseException {

    public InvalidOtpException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "INVALID_OTP");
    }
}
