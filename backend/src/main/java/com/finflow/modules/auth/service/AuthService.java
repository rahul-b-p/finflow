package com.finflow.modules.auth.service;

import com.finflow.core.exception.EmailAlreadyExistsException;
import com.finflow.core.security.IdObfuscationService;
import com.finflow.core.security.JwtTokenProvider;
import com.finflow.modules.auth.dto.request.RegistrationRequest;
import com.finflow.modules.auth.dto.response.RegistrationResponse;
import com.finflow.modules.foundation.entity.Role;
import com.finflow.modules.foundation.entity.User;
import com.finflow.modules.foundation.repository.RoleRepository;
import com.finflow.modules.foundation.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service orchestrating authentication, registration, and credentials management.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String DEFAULT_ROLE = "END_USER";
    private static final String PENDING_APPROVAL_STATUS = "PENDING_APPROVAL";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final SessionService sessionService;
    private final IdObfuscationService idObfuscationService;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       SessionService sessionService,
                       IdObfuscationService idObfuscationService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.sessionService = sessionService;
        this.idObfuscationService = idObfuscationService;
    }

    /**
     * Self-registers a new end-user. Holds account in PENDING_APPROVAL status.
     * Complies with Constitution: passwords hashed with BCrypt (factor 12), PII encrypted.
     */
    @Transactional
    public RegistrationResponse registerUser(RegistrationRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.findUserByEmail(normalizedEmail).isPresent()) {
            log.warn("Registration rejected: email {} already registered", normalizedEmail);
            throw new EmailAlreadyExistsException("An account with this email address already exists");
        }

        Role role = roleRepository.findByName(DEFAULT_ROLE)
                .orElseGet(() -> roleRepository.save(new Role(DEFAULT_ROLE, "Standard End User")));

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            user.setPhone(request.getPhone().trim());
        }
        user.setStatus(PENDING_APPROVAL_STATUS);
        user.setMustChangePassword(false);
        user.setTwoFactorEnabled(false);
        user.setFailedLoginAttempts(0);
        user.getRoles().add(role);

        userRepository.save(user);
        log.info("Successfully registered user {} in status {}", normalizedEmail, PENDING_APPROVAL_STATUS);

        return new RegistrationResponse(PENDING_APPROVAL_STATUS, "Your account has been registered and is pending administrator review.");
    }

    /**
     * Authenticates user credentials.
     * Enforces account status checks (PENDING_APPROVAL, SUSPENDED, REJECTED).
     * If 2FA is enabled, returns MFA_REQUIRED challenge token.
     * If 2FA is not enabled, establishes Redis session and issues JWT + refresh tokens.
     */
    @Transactional
    public com.finflow.modules.auth.dto.response.LoginResponse login(
            com.finflow.modules.auth.dto.request.LoginRequest request,
            String ipAddress,
            String userAgent) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        User user = userRepository.findUserByEmail(normalizedEmail)
                .orElseThrow(() -> {
                    log.warn("Login failed: email {} not found", normalizedEmail);
                    return new com.finflow.core.exception.InvalidCredentialsException();
                });

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            int failedAttempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(failedAttempts);
            userRepository.save(user);
            log.warn("Login failed: incorrect password for user {}. Failed attempts: {}", normalizedEmail, failedAttempts);
            throw new com.finflow.core.exception.InvalidCredentialsException();
        }

        // Account status checks
        String status = user.getStatus();
        if ("PENDING_APPROVAL".equalsIgnoreCase(status)) {
            log.warn("Login rejected: user {} is PENDING_APPROVAL", normalizedEmail);
            throw new com.finflow.core.exception.AccountNotActiveException(
                    "PENDING_APPROVAL",
                    "Account is in PENDING_APPROVAL status. Administrator approval is required before login."
            );
        } else if ("SUSPENDED".equalsIgnoreCase(status)) {
            log.warn("Login rejected: user {} is SUSPENDED", normalizedEmail);
            throw new com.finflow.core.exception.AccountNotActiveException(
                    "SUSPENDED",
                    "Account has been suspended. Please contact support."
            );
        } else if ("REJECTED".equalsIgnoreCase(status)) {
            log.warn("Login rejected: user {} is REJECTED", normalizedEmail);
            throw new com.finflow.core.exception.AccountNotActiveException(
                    "REJECTED",
                    "Account registration has been rejected."
            );
        } else if (!"ACTIVE".equalsIgnoreCase(status)) {
            log.warn("Login rejected: user {} has inactive status {}", normalizedEmail, status);
            throw new com.finflow.core.exception.AccountNotActiveException(
                    status,
                    "Account is not in active status."
            );
        }

        // Reset failed login attempts on successful credentials
        user.setFailedLoginAttempts(0);
        user.setLastLoginAt(java.time.Instant.now());
        userRepository.save(user);

        // If 2FA is active, issue temporary MFA challenge token
        if (user.isTwoFactorEnabled()) {
            log.info("User {} requires 2FA verification. Issuing MFA challenge token.", normalizedEmail);
            String mfaToken = jwtTokenProvider.generateMfaChallengeToken(user.getId());
            return new com.finflow.modules.auth.dto.response.MfaChallengeResponse(mfaToken, 300);
        }

        // Direct authentication: establish session in Redis
        java.util.List<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .toList();

        SessionService.SessionCreationResult session = sessionService.createSession(
                user.getId(),
                user.getStatus(),
                roleNames,
                ipAddress,
                userAgent
        );

        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getEmail(),
                roleNames,
                session.sessionId()
        );

        String obfuscatedId = idObfuscationService.obfuscate(user.getId(), session.sessionEncryptionKey());

        com.finflow.modules.auth.dto.response.UserProfileResponse profile =
                new com.finflow.modules.auth.dto.response.UserProfileResponse(
                        obfuscatedId,
                        user.getEmail(),
                        user.getFirstName(),
                        user.getLastName(),
                        user.getPhone(),
                        roleNames,
                        user.isTwoFactorEnabled()
                );

        int expiresInSec = (int) (jwtTokenProvider.getExpirationMs() / 1000);
        return new com.finflow.modules.auth.dto.response.AuthSuccessResponse(
                accessToken,
                session.refreshToken(),
                expiresInSec,
                profile
        );
    }
}
