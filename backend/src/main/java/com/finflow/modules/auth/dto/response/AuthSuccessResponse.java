package com.finflow.modules.auth.dto.response;

public class AuthSuccessResponse implements LoginResponse {

    private String status = "AUTHENTICATED";
    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private int expiresIn;
    private UserProfileResponse user;

    public AuthSuccessResponse() {
    }

    public AuthSuccessResponse(String accessToken, String refreshToken, int expiresIn, UserProfileResponse user) {
        this.status = "AUTHENTICATED";
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = "Bearer";
        this.expiresIn = expiresIn;
        this.user = user;
    }

    @Override
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public int getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(int expiresIn) {
        this.expiresIn = expiresIn;
    }

    public UserProfileResponse getUser() {
        return user;
    }

    public void setUser(UserProfileResponse user) {
        this.user = user;
    }
}
