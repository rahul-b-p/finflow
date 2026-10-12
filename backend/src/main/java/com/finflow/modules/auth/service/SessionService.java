package com.finflow.modules.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.config.JwtProperties;
import com.finflow.core.exception.IdDecryptionException;
import com.finflow.core.exception.InvalidCredentialsException;
import com.finflow.core.security.IdObfuscationService;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service managing Redis-backed distributed sessions, token rotation, and invalidation.
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private static final String SESSION_PREFIX = "auth:session:";
    private static final String REFRESH_PREFIX = "auth:refresh:";
    private static final String REFRESH_GRACE_PREFIX = "auth:refresh:grace:";
    private static final String USER_SESSIONS_PREFIX = "auth:user_sessions:";

    private static final Duration GRACE_WINDOW_DURATION = Duration.ofSeconds(15);
    private static final int MAX_DECRYPTION_FAILURES = 10;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final IdObfuscationService idObfuscationService;
    private final JwtProperties jwtProperties;

    public SessionService(StringRedisTemplate redisTemplate,
                          ObjectMapper objectMapper,
                          IdObfuscationService idObfuscationService,
                          JwtProperties jwtProperties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.idObfuscationService = idObfuscationService;
        this.jwtProperties = jwtProperties;
    }

    public record SessionCreationResult(String sessionId, String refreshToken, String sessionEncryptionKey) {}

    public record SessionRotationResult(String sessionId, UUID userId, String newRefreshToken, String sessionEncryptionKey, List<String> roles) {}

    /**
     * Creates an authenticated session in Redis, establishes the ephemeral AES-256 key, and issues a refresh token.
     */
    public SessionCreationResult createSession(UUID userId, String userStatus, List<String> roles, String ipAddress, String userAgent) {
        String sessionId = UUID.randomUUID().toString();
        String sessionEncryptionKey = idObfuscationService.generateSessionKey();
        Duration ttl = Duration.ofMillis(jwtProperties.getRefreshExpirationMs());

        SessionData sessionData = new SessionData(sessionId, userId, sessionEncryptionKey, userStatus, roles, ipAddress, userAgent);
        saveSession(sessionData, ttl);

        // Track session in user active sessions set
        String userSessionsKey = USER_SESSIONS_PREFIX + userId;
        redisTemplate.opsForSet().add(userSessionsKey, sessionId);
        redisTemplate.expire(userSessionsKey, ttl);

        // Generate and record refresh token
        String refreshToken = generateRefreshToken();
        saveRefreshToken(refreshToken, sessionId, userId, ttl);

        return new SessionCreationResult(sessionId, refreshToken, sessionEncryptionKey);
    }

    /**
     * Retrieves session data if present and active.
     */
    public Optional<SessionData> getSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        String json = redisTemplate.opsForValue().get(SESSION_PREFIX + sessionId);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, SessionData.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize session data for session: {}", sessionId, e);
            return Optional.empty();
        }
    }

    /**
     * Updates the last activity timestamp for sliding session tracking.
     */
    public void updateActivity(String sessionId) {
        getSession(sessionId).ifPresent(session -> {
            session.setLastActivityAt(Instant.now());
            saveSession(session, Duration.ofMillis(jwtProperties.getRefreshExpirationMs()));
        });
    }

    /**
     * Tracks decryption failures. If threshold is exceeded, automatically invalidates session.
     */
    public void incrementDecryptionFailures(String sessionId) {
        getSession(sessionId).ifPresent(session -> {
            int failures = session.getDecryptionFailures() + 1;
            session.setDecryptionFailures(failures);
            if (failures >= MAX_DECRYPTION_FAILURES) {
                log.warn("Session {} exceeded max decryption failure threshold ({}), terminating session", sessionId, failures);
                revokeSession(sessionId);
                throw new IdDecryptionException("Session terminated due to excessive decryption failures");
            } else {
                saveSession(session, Duration.ofMillis(jwtProperties.getRefreshExpirationMs()));
            }
        });
    }

    /**
     * Retrieves the AES encryption key for the active session.
     */
    public String getSessionEncryptionKey(String sessionId) {
        return getSession(sessionId)
                .map(SessionData::getSessionEncryptionKey)
                .orElseThrow(() -> new IdDecryptionException("Active session not found or expired for ID de-obfuscation"));
    }

    /**
     * Rotates a refresh token with 15-second grace period handling.
     */
    public SessionRotationResult rotateRefreshToken(String refreshToken, String ipAddress, String userAgent) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidCredentialsException("Refresh token must not be blank");
        }

        String tokenHash = hashToken(refreshToken);
        String refreshKey = REFRESH_PREFIX + tokenHash;
        String refreshJson = redisTemplate.opsForValue().get(refreshKey);

        if (refreshJson == null) {
            // Check grace window
            String graceJson = redisTemplate.opsForValue().get(REFRESH_GRACE_PREFIX + tokenHash);
            if (graceJson != null) {
                try {
                    SessionRotationResult cachedResult = objectMapper.readValue(graceJson, SessionRotationResult.class);
                    log.info("Returned cached rotated tokens from grace window for session {}", cachedResult.sessionId());
                    return cachedResult;
                } catch (Exception e) {
                    log.error("Failed to parse grace record", e);
                }
            }
            throw new InvalidCredentialsException("Invalid, expired, or revoked refresh token");
        }

        try {
            RefreshTokenData refreshData = objectMapper.readValue(refreshJson, RefreshTokenData.class);
            SessionData sessionData = getSession(refreshData.getSessionId())
                    .orElseThrow(() -> new InvalidCredentialsException("Session associated with refresh token is expired or revoked"));

            // Invalidate old refresh token
            redisTemplate.delete(refreshKey);

            // Generate new refresh token
            String newRefreshToken = generateRefreshToken();
            Duration ttl = Duration.ofMillis(jwtProperties.getRefreshExpirationMs());
            saveRefreshToken(newRefreshToken, sessionData.getSessionId(), sessionData.getUserId(), ttl);

            // Update session activity
            sessionData.setLastActivityAt(Instant.now());
            sessionData.setIpAddress(ipAddress);
            sessionData.setUserAgent(userAgent);
            saveSession(sessionData, ttl);

            SessionRotationResult result = new SessionRotationResult(
                    sessionData.getSessionId(),
                    sessionData.getUserId(),
                    newRefreshToken,
                    sessionData.getSessionEncryptionKey(),
                    sessionData.getRoles()
            );

            // Cache in grace window for concurrent requests (15s)
            try {
                redisTemplate.opsForValue().set(
                        REFRESH_GRACE_PREFIX + tokenHash,
                        objectMapper.writeValueAsString(result),
                        GRACE_WINDOW_DURATION
                );
            } catch (Exception e) {
                log.warn("Failed to store refresh grace record", e);
            }

            return result;
        } catch (InvalidCredentialsException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error during refresh token rotation", e);
            throw new InvalidCredentialsException("Failed to process refresh token rotation");
        }
    }

    /**
     * Revokes a specific session and removes it from the user's active session set.
     */
    public void revokeSession(String sessionId) {
        if (sessionId == null) return;
        getSession(sessionId).ifPresent(session -> {
            redisTemplate.delete(SESSION_PREFIX + sessionId);
            if (session.getUserId() != null) {
                redisTemplate.opsForSet().remove(USER_SESSIONS_PREFIX + session.getUserId(), sessionId);
            }
        });
    }

    /**
     * Revokes all active sessions for a user (e.g. during password reset or account lock).
     */
    public void revokeUserSessions(UUID userId) {
        if (userId == null) return;
        String userSessionsKey = USER_SESSIONS_PREFIX + userId;
        Set<String> sessionIds = redisTemplate.opsForSet().members(userSessionsKey);
        if (sessionIds != null && !sessionIds.isEmpty()) {
            for (String sid : sessionIds) {
                redisTemplate.delete(SESSION_PREFIX + sid);
            }
        }
        redisTemplate.delete(userSessionsKey);
    }

    private void saveSession(SessionData sessionData, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(sessionData);
            redisTemplate.opsForValue().set(SESSION_PREFIX + sessionData.getSessionId(), json, ttl);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize session data", e);
        }
    }

    private void saveRefreshToken(String refreshToken, String sessionId, UUID userId, Duration ttl) {
        try {
            String hash = hashToken(refreshToken);
            RefreshTokenData data = new RefreshTokenData(sessionId, userId, Instant.now().plus(ttl));
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(REFRESH_PREFIX + hash, json, ttl);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize refresh token data", e);
        }
    }

    private String generateRefreshToken() {
        byte[] randomBytes = new byte[24];
        SECURE_RANDOM.nextBytes(randomBytes);
        return "ff_ref_" + Hex.encodeHexString(randomBytes);
    }

    private String hashToken(String token) {
        return DigestUtils.sha256Hex(token);
    }
}
