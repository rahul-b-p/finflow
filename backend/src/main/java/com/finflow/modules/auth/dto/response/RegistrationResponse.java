package com.finflow.modules.auth.dto.response;

public class RegistrationResponse {

    private String status = "PENDING_APPROVAL";
    private String message = "Your account has been registered and is pending administrator review.";

    public RegistrationResponse() {
    }

    public RegistrationResponse(String status, String message) {
        this.status = status;
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
