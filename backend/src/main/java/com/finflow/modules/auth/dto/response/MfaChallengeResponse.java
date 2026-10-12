package com.finflow.modules.auth.dto.response;

public class MfaChallengeResponse implements LoginResponse {

    private String status = "MFA_REQUIRED";
    private String mfaToken;
    private int expiresIn = 300;

    public MfaChallengeResponse() {
    }

    public MfaChallengeResponse(String mfaToken, int expiresIn) {
        this.status = "MFA_REQUIRED";
        this.mfaToken = mfaToken;
        this.expiresIn = expiresIn;
    }

    @Override
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMfaToken() {
        return mfaToken;
    }

    public void setMfaToken(String mfaToken) {
        this.mfaToken = mfaToken;
    }

    public int getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(int expiresIn) {
        this.expiresIn = expiresIn;
    }
}
