package com.finflow.core.exception;

import org.springframework.http.HttpStatusCode;

public abstract class BaseException extends RuntimeException {

    private final HttpStatusCode status;
    private final String errorCode;

    protected BaseException(String message, HttpStatusCode status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    protected BaseException(String message, Throwable cause, HttpStatusCode status, String errorCode) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatusCode getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
