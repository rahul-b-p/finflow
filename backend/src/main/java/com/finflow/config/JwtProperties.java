package com.finflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for JSON Web Token (JWT) management.
 */
@Component
@ConfigurationProperties(prefix = "finflow.security.jwt")
public class JwtProperties {

    /**
     * Base64-encoded secret key used for HMAC-SHA256 signing.
     */
    private String secret = "dGhpc2lzYXZlcnlzZWNyZXRrZXlmb3Jqd3RzaWduaW5nMTIzNDU=";

    /**
     * Access token expiration in milliseconds (default: 15 minutes = 900,000 ms).
     */
    private long expirationMs = 900000L;

    /**
     * Refresh token expiration in milliseconds (default: 7 days = 604,800,000 ms).
     */
    private long refreshExpirationMs = 604800000L;

    /**
     * Token issuer URI.
     */
    private String issuer = "https://api.finflow.com";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }

    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }

    public void setRefreshExpirationMs(long refreshExpirationMs) {
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
