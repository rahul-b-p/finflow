package com.finflow.core.startup;

import com.finflow.core.exception.InvalidConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@Order(3)
public class RedisHealthValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RedisHealthValidator.class);

    private final RedisConnectionFactory connectionFactory;
    private final Environment environment;

    public RedisHealthValidator(RedisConnectionFactory connectionFactory, Environment environment) {
        this.connectionFactory = connectionFactory;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        boolean isStrict = activeProfiles.contains("staging") || activeProfiles.contains("production");

        try (RedisConnection connection = connectionFactory.getConnection()) {
            String pingResult = connection.ping();
            log.info("Redis health probe succeeded at startup: [{}]", pingResult);
        } catch (Exception e) {
            if (isStrict) {
                log.error("CRITICAL: Redis instance is unreachable in {} profile: {}", activeProfiles, e.getMessage());
                throw new InvalidConfigurationException("Redis connection failed during startup health validation", e);
            } else {
                log.warn("WARNING: Redis instance is unreachable in non-production profile. Continuing startup with degraded cache capabilities: {}", e.getMessage());
            }
        }
    }
}
