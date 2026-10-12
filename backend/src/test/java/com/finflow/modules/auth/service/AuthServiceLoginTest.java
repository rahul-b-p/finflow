package com.finflow.modules.auth.service;

import com.finflow.core.exception.AccountNotActiveException;
import com.finflow.core.exception.InvalidCredentialsException;
import com.finflow.core.security.IdObfuscationService;
import com.finflow.core.security.JwtTokenProvider;
import com.finflow.modules.auth.dto.request.LoginRequest;
import com.finflow.modules.auth.dto.response.AuthSuccessResponse;
import com.finflow.modules.auth.dto.response.LoginResponse;
import com.finflow.modules.auth.dto.response.MfaChallengeResponse;
import com.finflow.modules.foundation.entity.Role;
import com.finflow.modules.foundation.entity.User;
import com.finflow.modules.foundation.repository.RoleRepository;
import com.finflow.modules.foundation.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceLoginTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private SessionService sessionService;

    @Mock
    private IdObfuscationService idObfuscationService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                roleRepository,
                passwordEncoder,
                jwtTokenProvider,
                sessionService,
                idObfuscationService
        );
    }

    private User createTestUser(String status, boolean twoFactorEnabled) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@finflow.internal");
        user.setPasswordHash("$2a$12$hashedPassword");
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setStatus(status);
        user.setTwoFactorEnabled(twoFactorEnabled);
        user.setFailedLoginAttempts(0);
        user.getRoles().add(new Role("END_USER", "Standard End User"));
        return user;
    }

    @Test
    @DisplayName("Direct login succeeds for active user without 2FA and returns session tokens with obfuscated ID")
    void shouldLoginSuccessfullyWithout2fa() {
        User user = createTestUser("ACTIVE", false);
        LoginRequest request = new LoginRequest("user@finflow.internal", "ValidPassword123!");

        when(userRepository.findUserByEmail("user@finflow.internal")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("ValidPassword123!", user.getPasswordHash())).thenReturn(true);

        SessionService.SessionCreationResult sessionResult = new SessionService.SessionCreationResult(
                "session-uuid-123",
                "ff_ref_sample_refresh_token",
                "secret-encryption-key-for-session"
        );
        when(sessionService.createSession(eq(user.getId()), eq("ACTIVE"), anyList(), anyString(), anyString()))
                .thenReturn(sessionResult);
        when(jwtTokenProvider.generateAccessToken(eq(user.getId()), eq(user.getEmail()), anyList(), eq("session-uuid-123")))
                .thenReturn("access-jwt-token");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(900000L);
        when(idObfuscationService.obfuscate(eq(user.getId()), eq("secret-encryption-key-for-session")))
                .thenReturn("id_v1_obfuscated_token");

        LoginResponse response = authService.login(request, "127.0.0.1", "Mozilla/5.0");

        assertThat(response).isInstanceOf(AuthSuccessResponse.class);
        AuthSuccessResponse success = (AuthSuccessResponse) response;
        assertThat(success.getStatus()).isEqualTo("AUTHENTICATED");
        assertThat(success.getAccessToken()).isEqualTo("access-jwt-token");
        assertThat(success.getRefreshToken()).isEqualTo("ff_ref_sample_refresh_token");
        assertThat(success.getUser().getId()).isEqualTo("id_v1_obfuscated_token");
        assertThat(success.getUser().getEmail()).isEqualTo("user@finflow.internal");

        verify(userRepository).save(user);
        assertThat(user.getFailedLoginAttempts()).isEqualTo(0);
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("Login returns MFA challenge token when active user has twoFactorEnabled = true")
    void shouldReturnMfaChallengeWhen2faEnabled() {
        User user = createTestUser("ACTIVE", true);
        LoginRequest request = new LoginRequest("user@finflow.internal", "ValidPassword123!");

        when(userRepository.findUserByEmail("user@finflow.internal")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("ValidPassword123!", user.getPasswordHash())).thenReturn(true);
        when(jwtTokenProvider.generateMfaChallengeToken(user.getId())).thenReturn("mfa-challenge-jwt");

        LoginResponse response = authService.login(request, "127.0.0.1", "Mozilla/5.0");

        assertThat(response).isInstanceOf(MfaChallengeResponse.class);
        MfaChallengeResponse challenge = (MfaChallengeResponse) response;
        assertThat(challenge.getStatus()).isEqualTo("MFA_REQUIRED");
        assertThat(challenge.getMfaToken()).isEqualTo("mfa-challenge-jwt");
        assertThat(challenge.getExpiresIn()).isEqualTo(300);

        verify(sessionService, never()).createSession(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Throws AccountNotActiveException when user status is PENDING_APPROVAL")
    void shouldRejectPendingApprovalUser() {
        User user = createTestUser("PENDING_APPROVAL", false);
        LoginRequest request = new LoginRequest("user@finflow.internal", "ValidPassword123!");

        when(userRepository.findUserByEmail("user@finflow.internal")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("ValidPassword123!", user.getPasswordHash())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request, "127.0.0.1", "Mozilla/5.0"))
                .isInstanceOf(AccountNotActiveException.class)
                .hasMessageContaining("PENDING_APPROVAL");
    }

    @Test
    @DisplayName("Throws AccountNotActiveException when user status is SUSPENDED")
    void shouldRejectSuspendedUser() {
        User user = createTestUser("SUSPENDED", false);
        LoginRequest request = new LoginRequest("user@finflow.internal", "ValidPassword123!");

        when(userRepository.findUserByEmail("user@finflow.internal")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("ValidPassword123!", user.getPasswordHash())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request, "127.0.0.1", "Mozilla/5.0"))
                .isInstanceOf(AccountNotActiveException.class)
                .hasMessageContaining("suspended");
    }

    @Test
    @DisplayName("Throws InvalidCredentialsException and increments failed attempts on bad password")
    void shouldIncrementFailedAttemptsOnWrongPassword() {
        User user = createTestUser("ACTIVE", false);
        LoginRequest request = new LoginRequest("user@finflow.internal", "WrongPassword!");

        when(userRepository.findUserByEmail("user@finflow.internal")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword!", user.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request, "127.0.0.1", "Mozilla/5.0"))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        verify(userRepository).save(user);
    }
}
