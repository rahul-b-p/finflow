package com.finflow.modules.auth.service;

import com.finflow.core.exception.EmailAlreadyExistsException;
import com.finflow.modules.auth.dto.request.RegistrationRequest;
import com.finflow.modules.auth.dto.response.RegistrationResponse;
import com.finflow.modules.foundation.entity.Role;
import com.finflow.modules.foundation.entity.User;
import com.finflow.modules.foundation.repository.RoleRepository;
import com.finflow.modules.foundation.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private com.finflow.core.security.JwtTokenProvider jwtTokenProvider;

    @Mock
    private com.finflow.modules.auth.service.SessionService sessionService;

    @Mock
    private com.finflow.core.security.IdObfuscationService idObfuscationService;

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

    @Test
    @DisplayName("Self-registration successfully creates a user in PENDING_APPROVAL status with hashed password")
    void shouldRegisterUserSuccessfully() {
        RegistrationRequest request = new RegistrationRequest(
                "Jane.Doe@FinFlow.internal",
                "StrongP@ssw0rd!",
                "Jane",
                "Doe",
                "+1234567890"
        );

        when(userRepository.findUserByEmail("jane.doe@finflow.internal")).thenReturn(Optional.empty());
        when(roleRepository.findByName("END_USER")).thenReturn(Optional.of(new Role("END_USER", "Standard End User")));
        when(passwordEncoder.encode("StrongP@ssw0rd!")).thenReturn("$2a$12$hashedPassword");

        RegistrationResponse response = authService.registerUser(request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(response.getMessage()).contains("pending administrator review");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("jane.doe@finflow.internal");
        assertThat(savedUser.getPasswordHash()).isEqualTo("$2a$12$hashedPassword");
        assertThat(savedUser.getFirstName()).isEqualTo("Jane");
        assertThat(savedUser.getLastName()).isEqualTo("Doe");
        assertThat(savedUser.getPhone()).isEqualTo("+1234567890");
        assertThat(savedUser.getStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(savedUser.isMustChangePassword()).isFalse();
        assertThat(savedUser.isTwoFactorEnabled()).isFalse();
        assertThat(savedUser.getFailedLoginAttempts()).isEqualTo(0);
        assertThat(savedUser.getRoles()).extracting(Role::getName).contains("END_USER");
    }

    @Test
    @DisplayName("Throws EmailAlreadyExistsException when attempting to register with an already registered email")
    void shouldRejectDuplicateEmailRegistration() {
        RegistrationRequest request = new RegistrationRequest(
                "existing@finflow.internal",
                "StrongP@ssw0rd!",
                "Existing",
                "User",
                null
        );

        when(userRepository.findUserByEmail("existing@finflow.internal")).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> authService.registerUser(request))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("already exists");

        verify(userRepository, never()).save(any());
    }
}
