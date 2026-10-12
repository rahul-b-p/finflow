package com.finflow.core.startup;

import com.finflow.modules.foundation.entity.Role;
import com.finflow.modules.foundation.entity.User;
import com.finflow.modules.foundation.repository.RoleRepository;
import com.finflow.modules.foundation.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private SuperAdminInitializer superAdminInitializer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(superAdminInitializer, "adminEmail", "admin@finflow.internal");
        ReflectionTestUtils.setField(superAdminInitializer, "adminPassword", "AdminSecurePassword123!");
        ReflectionTestUtils.setField(superAdminInitializer, "adminFirstName", "Super");
        ReflectionTestUtils.setField(superAdminInitializer, "adminLastName", "Admin");
        ReflectionTestUtils.setField(superAdminInitializer, "adminPhone", "+1234567890");
    }

    @Test
    @DisplayName("Provisions Super Admin when 0 super admins exist")
    void shouldSeedSuperAdminWhenNoneExists() {
        when(userRepository.countByRoleName("SUPER_ADMIN")).thenReturn(0L);
        when(roleRepository.findByName("SUPER_ADMIN")).thenReturn(Optional.of(new Role("SUPER_ADMIN", "Super Admin role")));
        when(passwordEncoder.encode("AdminSecurePassword123!")).thenReturn("hashed_password_xyz");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        superAdminInitializer.run(new DefaultApplicationArguments());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User seeded = captor.getValue();

        assertThat(seeded.getEmail()).isEqualTo("admin@finflow.internal");
        assertThat(seeded.getPasswordHash()).isEqualTo("hashed_password_xyz");
        assertThat(seeded.getStatus()).isEqualTo("ACTIVE");
        assertThat(seeded.isMustChangePassword()).isTrue();
        assertThat(seeded.getRoles()).extracting(Role::getName).contains("SUPER_ADMIN");
    }

    @Test
    @DisplayName("Idempotent: skips seeding when Super Admin already exists")
    void shouldSkipSeedingWhenSuperAdminAlreadyExists() {
        when(userRepository.countByRoleName("SUPER_ADMIN")).thenReturn(1L);

        superAdminInitializer.run(new DefaultApplicationArguments());

        verify(userRepository, never()).save(any());
        verify(roleRepository, never()).save(any());
    }
}
