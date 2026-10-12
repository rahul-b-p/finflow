package com.finflow.core.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown or signaled when MFA verification is required to complete authentication.
 */
public class MfaRequiredException extends BaseException {

    private final String mfaToken;
    private final int expiresIn;

    public MfaRequiredException(String mfaToken, int expiresIn) {
        super("Multi-factor authentication required", HttpStatus.UNAUTHORIZED, "MFA_REQUIRED");
        this.mfaToken = mfaToken;
        this.expiresIn = expiresIn;
    }

    public String getMfaToken() {
        return mfaToken;
    }

    public int getExpiresIn() {
        return expiresIn;
    }
}
