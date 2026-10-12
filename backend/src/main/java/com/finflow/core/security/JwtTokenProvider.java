package com.finflow.core.security;

import com.finflow.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * JWT token utility supporting access tokens, MFA challenge tokens, and claims validation.
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_SESSION_ID = "session_id";
    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_MFA_CHALLENGE = "MFA_CHALLENGE";
    private static final long MFA_CHALLENGE_EXPIRATION_MS = 300000L; // 5 minutes

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.signingKey = initKey(jwtProperties.getSecret());
    }

    private SecretKey initKey(String secret) {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
            if (keyBytes.length < 32) {
                keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generates a 15-minute access token containing user identity and session ID.
     */
    public String generateAccessToken(UUID userId, String email, List<String> roles, String sessionId) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(jwtProperties.getExpirationMs());

        return Jwts.builder()
                .subject(userId.toString())
                .issuer(jwtProperties.getIssuer())
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_SESSION_ID, sessionId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Generates a 5-minute temporary MFA challenge token.
     */
    public String generateMfaChallengeToken(UUID userId) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(MFA_CHALLENGE_EXPIRATION_MS);

        return Jwts.builder()
                .subject(userId.toString())
                .issuer(jwtProperties.getIssuer())
                .claim(CLAIM_TYPE, TYPE_MFA_CHALLENGE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validates signature and expiration with 60 seconds clock skew leeway.
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(signingKey)
                    .clockSkewSeconds(60)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extracts claims from a valid token.
     */
    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .clockSkewSeconds(60)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID getUserIdFromToken(String token) {
        return UUID.fromString(getClaims(token).getSubject());
    }

    public String getSessionIdFromToken(String token) {
        return (String) getClaims(token).get(CLAIM_SESSION_ID);
    }

    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        Object rolesObj = getClaims(token).get(CLAIM_ROLES);
        if (rolesObj instanceof List<?> list) {
            return (List<String>) list;
        }
        return List.of();
    }

    public String getEmailFromToken(String token) {
        return (String) getClaims(token).get(CLAIM_EMAIL);
    }

    public boolean isMfaChallengeToken(String token) {
        try {
            Claims claims = getClaims(token);
            return TYPE_MFA_CHALLENGE.equals(claims.get(CLAIM_TYPE));
        } catch (Exception e) {
            return false;
        }
    }

    public long getExpirationMs() {
        return jwtProperties.getExpirationMs();
    }

    public long getRefreshExpirationMs() {
        return jwtProperties.getRefreshExpirationMs();
    }
}
