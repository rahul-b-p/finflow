package com.finflow.core.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when user credentials (email/password) are invalid or not found.
 */
public class InvalidCredentialsException extends BaseException {

    public InvalidCredentialsException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
    }

    public InvalidCredentialsException() {
        this("Invalid email or password");
    }
}
