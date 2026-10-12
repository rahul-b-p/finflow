package com.finflow.core.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a user attempts authentication or action but account is not in ACTIVE status.
 */
public class AccountNotActiveException extends BaseException {

    public AccountNotActiveException(String message) {
        super(message, HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ACTIVE");
    }

    public AccountNotActiveException(String status, String message) {
        super(message, HttpStatus.FORBIDDEN, "ACCOUNT_" + status);
    }
}
