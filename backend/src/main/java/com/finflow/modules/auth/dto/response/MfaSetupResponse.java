package com.finflow.modules.auth.dto.response;

public class MfaSetupResponse {

    private String secret;
    private String qrCodeUri;

    public MfaSetupResponse() {
    }

    public MfaSetupResponse(String secret, String qrCodeUri) {
        this.secret = secret;
        this.qrCodeUri = qrCodeUri;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getQrCodeUri() {
        return qrCodeUri;
    }

    public void setQrCodeUri(String qrCodeUri) {
        this.qrCodeUri = qrCodeUri;
    }
}
