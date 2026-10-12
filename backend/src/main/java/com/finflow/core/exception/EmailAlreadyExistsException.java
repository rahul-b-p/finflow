package com.finflow.core.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an account with the requested email already exists.
 */
public class EmailAlreadyExistsException extends BaseException {

    public EmailAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS");
    }
}
