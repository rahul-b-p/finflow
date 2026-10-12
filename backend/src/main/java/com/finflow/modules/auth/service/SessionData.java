package com.finflow.modules.auth.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionData {

    private String sessionId;
    private UUID userId;
    private String sessionEncryptionKey;
    private int decryptionFailures;
    private String userStatus;
    private List<String> roles;
    private Instant createdAt;
    private Instant lastActivityAt;
    private String ipAddress;
    private String userAgent;

    public SessionData() {
    }

    public SessionData(String sessionId, UUID userId, String sessionEncryptionKey, String userStatus,
                       List<String> roles, String ipAddress, String userAgent) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.sessionEncryptionKey = sessionEncryptionKey;
        this.decryptionFailures = 0;
        this.userStatus = userStatus;
        this.roles = roles;
        this.createdAt = Instant.now();
        this.lastActivityAt = Instant.now();
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getSessionEncryptionKey() {
        return sessionEncryptionKey;
    }

    public void setSessionEncryptionKey(String sessionEncryptionKey) {
        this.sessionEncryptionKey = sessionEncryptionKey;
    }

    public int getDecryptionFailures() {
        return decryptionFailures;
    }

    public void setDecryptionFailures(int decryptionFailures) {
        this.decryptionFailures = decryptionFailures;
    }

    public String getUserStatus() {
        return userStatus;
    }

    public void setUserStatus(String userStatus) {
        this.userStatus = userStatus;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public void setLastActivityAt(Instant lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
}
