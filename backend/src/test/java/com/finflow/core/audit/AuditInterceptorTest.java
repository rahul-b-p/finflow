package com.finflow.core.audit;

import com.finflow.modules.foundation.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditInterceptorTest {

    @Mock
    private EntityAuditService auditService;

    private EntityDiffGenerator diffGenerator;
    private AuditInterceptor interceptor;

    @BeforeEach
    void setUp() {
        diffGenerator = new EntityDiffGenerator();
        AuditInterceptor.registerServices(auditService, diffGenerator);
        interceptor = new AuditInterceptor();
        MDC.clear();
    }

    @Test
    @DisplayName("Captures insert mutation and scrubs PII from diffData")
    void shouldCaptureInsertMutationWithoutPii() {
        MDC.put("clientIp", "192.168.1.100");
        MDC.put("actor", "ADMIN_USER");

        User user = new User();
        UUID userId = UUID.randomUUID();
        user.setId(userId);
        user.setEmail("admin@test.internal");
        user.setPasswordHash("hashed_secret");
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setPhone("+1234567890");

        interceptor.onPostPersist(user);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> diffCaptor = ArgumentCaptor.forClass(Map.class);

        verify(auditService).recordMutation(
                eq("users"),
                eq(userId),
                eq("INSERT"),
                diffCaptor.capture(),
                eq("ADMIN_USER"),
                eq("192.168.1.100")
        );

        Map<String, Object> diff = diffCaptor.getValue();
        assertThat(diff).isNotNull();

        // Verify password and PII are masked
        @SuppressWarnings("unchecked")
        Map<String, Object> passwordChange = (Map<String, Object>) diff.get("passwordHash");
        assertThat(passwordChange.get("new")).isEqualTo("[PASSWORD_MASKED]");

        @SuppressWarnings("unchecked")
        Map<String, Object> emailChange = (Map<String, Object>) diff.get("email");
        assertThat(emailChange.get("new")).isEqualTo("[PII_MASKED]");
    }
}
