package com.finflow.core.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        MDC.clear();
    }

    @Test
    @DisplayName("Handles ResourceNotFoundException with 404 RFC 7807 problem detail")
    void shouldHandleResourceNotFoundException() {
        MDC.put("correlationId", "corr-test-123");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/non-existent");

        ResourceNotFoundException ex = new ResourceNotFoundException("User", "admin@finflow.internal");
        ResponseEntity<ProblemDetailResponse> response = exceptionHandler.handleBaseException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(response.getBody().getCorrelationId()).isEqualTo("corr-test-123");
        assertThat(response.getBody().getInstance()).isEqualTo("/api/v1/users/non-existent");
    }

    @Test
    @DisplayName("Handles generic Exception with 500 RFC 7807 problem detail")
    void shouldHandleGenericException() {
        MDC.put("correlationId", "corr-test-500");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/users");

        RuntimeException ex = new RuntimeException("Unexpected fatal failure");
        ResponseEntity<ProblemDetailResponse> response = exceptionHandler.handleGeneralException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getCode()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.getBody().getCorrelationId()).isEqualTo("corr-test-500");
    }
}
