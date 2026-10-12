package com.finflow.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class MfaVerificationRequest {

    @NotBlank(message = "MFA challenge token is required")
    private String mfaToken;

    @NotBlank(message = "Verification code is required")
    @Pattern(regexp = "^[0-9]{6}$", message = "Verification code must be exactly 6 digits")
    private String code;

    public MfaVerificationRequest() {
    }

    public MfaVerificationRequest(String mfaToken, String code) {
        this.mfaToken = mfaToken;
        this.code = code;
    }

    public String getMfaToken() {
        return mfaToken;
    }

    public void setMfaToken(String mfaToken) {
        this.mfaToken = mfaToken;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
