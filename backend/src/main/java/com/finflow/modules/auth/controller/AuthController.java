package com.finflow.modules.auth.controller;

import com.finflow.modules.auth.dto.request.RegistrationRequest;
import com.finflow.modules.auth.dto.response.RegistrationResponse;
import com.finflow.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing authentication lifecycle and registration endpoints.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Endpoints for user authentication, registration, session, and 2FA")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Self-register a new user account", description = "Registers a new user into PENDING_APPROVAL status for administrative review.")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegistrationRequest request) {
        RegistrationResponse response = authService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "User Login & Session Issuance", description = "Authenticates credentials and establishes session or issues MFA challenge.")
    public ResponseEntity<com.finflow.modules.auth.dto.response.LoginResponse> login(
            @Valid @RequestBody com.finflow.modules.auth.dto.request.LoginRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        String ip = httpRequest.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = httpRequest.getRemoteAddr();
        }
        String userAgent = httpRequest.getHeader("User-Agent");
        if (userAgent == null) {
            userAgent = "Unknown";
        }

        com.finflow.modules.auth.dto.response.LoginResponse response = authService.login(request, ip, userAgent);
        return ResponseEntity.ok(response);
    }
}
