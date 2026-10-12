package com.finflow.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class DisableMfaRequest {

    @NotBlank(message = "Password is required to disable 2FA")
    private String password;

    @NotBlank(message = "Verification code is required to disable 2FA")
    @Pattern(regexp = "^[0-9]{6}$", message = "Verification code must be exactly 6 digits")
    private String code;

    public DisableMfaRequest() {
    }

    public DisableMfaRequest(String password, String code) {
        this.password = password;
        this.code = code;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
