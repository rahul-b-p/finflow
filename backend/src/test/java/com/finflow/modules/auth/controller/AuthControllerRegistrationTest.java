package com.finflow.modules.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.core.exception.EmailAlreadyExistsException;
import com.finflow.core.exception.GlobalExceptionHandler;
import com.finflow.modules.auth.dto.request.RegistrationRequest;
import com.finflow.modules.auth.dto.response.RegistrationResponse;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerRegistrationTest {

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
    @DisplayName("POST /api/v1/auth/register returns 201 Created and PENDING_APPROVAL status for valid payload")
    void shouldReturn201ForValidRegistration() throws Exception {
        RegistrationRequest request = new RegistrationRequest(
                "newuser@finflow.internal",
                "StrongP@ssw0rd!",
                "John",
                "Doe",
                "+1234567890"
        );

        when(authService.registerUser(any(RegistrationRequest.class)))
                .thenReturn(new RegistrationResponse("PENDING_APPROVAL", "Your account has been registered and is pending administrator review."));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/auth/register returns 409 Conflict when email already exists")
    void shouldReturn409ForDuplicateEmail() throws Exception {
        RegistrationRequest request = new RegistrationRequest(
                "existing@finflow.internal",
                "StrongP@ssw0rd!",
                "John",
                "Doe",
                null
        );

        when(authService.registerUser(any(RegistrationRequest.class)))
                .thenThrow(new EmailAlreadyExistsException("An account with this email address already exists"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register returns 400 Bad Request for weak password")
    void shouldReturn400ForWeakPassword() throws Exception {
        RegistrationRequest request = new RegistrationRequest(
                "user@finflow.internal",
                "weak",
                "John",
                "Doe",
                null
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
