package com.finflow.config;

import com.finflow.core.exception.InvalidConfigurationException;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "finflow.cors")
public class CorsProperties {

    private final Environment environment;

    private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173", "http://localhost:3000"));
    private List<String> allowedMethods = new ArrayList<>(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    private List<String> allowedHeaders = new ArrayList<>(List.of("*"));
    private List<String> exposedHeaders = new ArrayList<>(List.of("X-Correlation-ID"));
    private boolean allowCredentials = true;
    private long maxAge = 3600;

    public CorsProperties(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        boolean isStrict = activeProfiles.contains("staging") || activeProfiles.contains("production");

        if (isStrict) {
            for (String origin : allowedOrigins) {
                if ("*".equals(origin.trim())) {
                    throw new InvalidConfigurationException("Wildcard '*' CORS allowed-origins is strictly prohibited in staging and production");
                }
            }
        }
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public List<String> getAllowedMethods() {
        return allowedMethods;
    }

    public void setAllowedMethods(List<String> allowedMethods) {
        this.allowedMethods = allowedMethods;
    }

    public List<String> getAllowedHeaders() {
        return allowedHeaders;
    }

    public void setAllowedHeaders(List<String> allowedHeaders) {
        this.allowedHeaders = allowedHeaders;
    }

    public List<String> getExposedHeaders() {
        return exposedHeaders;
    }

    public void setExposedHeaders(List<String> exposedHeaders) {
        this.exposedHeaders = exposedHeaders;
    }

    public boolean isAllowCredentials() {
        return allowCredentials;
    }

    public void setAllowCredentials(boolean allowCredentials) {
        this.allowCredentials = allowCredentials;
    }

    public long getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(long maxAge) {
        this.maxAge = maxAge;
    }
}
