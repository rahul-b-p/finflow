package com.finflow.core.exception;

import org.springframework.http.HttpStatus;

public class InvalidConfigurationException extends BaseException {

    public InvalidConfigurationException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, "INVALID_CONFIGURATION");
    }

    public InvalidConfigurationException(String message, Throwable cause) {
        super(message, cause, HttpStatus.INTERNAL_SERVER_ERROR, "INVALID_CONFIGURATION");
    }
}
