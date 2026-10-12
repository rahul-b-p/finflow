package com.finflow.config;

import com.finflow.core.exception.InvalidConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorsConfigTest {

    @Mock
    private Environment environment;

    @Test
    @DisplayName("Permits configured origins in dev environment")
    void shouldPermitConfiguredOrigins() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});

        CorsProperties properties = new CorsProperties(environment);
        properties.setAllowedOrigins(List.of("http://localhost:5173"));
        properties.validate();

        CorsConfig corsConfig = new CorsConfig();
        CorsConfigurationSource source = corsConfig.corsConfigurationSource(properties);

        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setRequestURI("/actuator/health");
        CorsConfiguration config = source.getCorsConfiguration(request);

        assertThat(config).isNotNull();
        assertThat(config.getAllowedOrigins()).contains("http://localhost:5173");
        assertThat(config.getExposedHeaders()).contains("X-Correlation-ID");
    }

    @Test
    @DisplayName("Rejects wildcard '*' origin in production environment")
    void shouldRejectWildcardInProduction() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"production"});

        CorsProperties properties = new CorsProperties(environment);
        properties.setAllowedOrigins(List.of("*"));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("Wildcard '*' CORS allowed-origins is strictly prohibited");
    }
}
