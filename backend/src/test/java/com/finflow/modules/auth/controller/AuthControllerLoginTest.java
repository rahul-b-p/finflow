package com.finflow.modules.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.core.exception.AccountNotActiveException;
import com.finflow.core.exception.GlobalExceptionHandler;
import com.finflow.core.exception.InvalidCredentialsException;
import com.finflow.modules.auth.dto.request.LoginRequest;
import com.finflow.modules.auth.dto.response.AuthSuccessResponse;
import com.finflow.modules.auth.dto.response.MfaChallengeResponse;
import com.finflow.modules.auth.dto.response.UserProfileResponse;
import com.finflow.modules.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerLoginTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/auth/login returns 200 OK with AuthSuccessResponse for active user without 2FA")
    void shouldReturn200ForDirectLogin() throws Exception {
        LoginRequest request = new LoginRequest("user@finflow.internal", "Password123!");
        UserProfileResponse profile = new UserProfileResponse(
                "id_v1_obfuscated_token",
                "user@finflow.internal",
                "John",
                "Doe",
                null,
                List.of("END_USER"),
                false
        );
        AuthSuccessResponse authSuccess = new AuthSuccessResponse(
                "access.jwt.token",
                "ff_ref_123456",
                900,
                profile
        );

        when(authService.login(any(LoginRequest.class), anyString(), anyString())).thenReturn(authSuccess);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"))
                .andExpect(jsonPath("$.accessToken").value("access.jwt.token"))
                .andExpect(jsonPath("$.refreshToken").value("ff_ref_123456"))
                .andExpect(jsonPath("$.user.id").value("id_v1_obfuscated_token"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login returns 200 OK with MfaChallengeResponse when 2FA is required")
    void shouldReturn200WithMfaChallengeWhen2faEnabled() throws Exception {
        LoginRequest request = new LoginRequest("user@finflow.internal", "Password123!");
        MfaChallengeResponse challenge = new MfaChallengeResponse("mfa.challenge.jwt", 300);

        when(authService.login(any(LoginRequest.class), anyString(), anyString())).thenReturn(challenge);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MFA_REQUIRED"))
                .andExpect(jsonPath("$.mfaToken").value("mfa.challenge.jwt"))
                .andExpect(jsonPath("$.expiresIn").value(300));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login returns 403 Forbidden when user is PENDING_APPROVAL")
    void shouldReturn403WhenPendingApproval() throws Exception {
        LoginRequest request = new LoginRequest("user@finflow.internal", "Password123!");

        when(authService.login(any(LoginRequest.class), anyString(), anyString()))
                .thenThrow(new AccountNotActiveException("PENDING_APPROVAL", "Account is awaiting administrator review"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("ACCOUNT_PENDING_APPROVAL"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login returns 401 Unauthorized when credentials are invalid")
    void shouldReturn401WhenInvalidCredentials() throws Exception {
        LoginRequest request = new LoginRequest("user@finflow.internal", "WrongPassword!");

        when(authService.login(any(LoginRequest.class), anyString(), anyString()))
                .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }
}
