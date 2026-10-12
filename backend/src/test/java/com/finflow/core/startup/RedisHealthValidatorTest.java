package com.finflow.core.startup;

import com.finflow.core.exception.InvalidConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisHealthValidatorTest {

    @Mock
    private RedisConnectionFactory connectionFactory;

    @Mock
    private RedisConnection connection;

    @Mock
    private Environment environment;

    @Test
    @DisplayName("Dev profile: logs warning and continues when Redis fails")
    void shouldWarnAndContinueInDev() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        when(connectionFactory.getConnection()).thenThrow(new RedisConnectionFailureException("Connection refused"));

        RedisHealthValidator validator = new RedisHealthValidator(connectionFactory, environment);

        assertThatCode(() -> validator.run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Production profile: throws InvalidConfigurationException when Redis fails")
    void shouldFailFastInProduction() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"production"});
        when(connectionFactory.getConnection()).thenThrow(new RedisConnectionFailureException("Connection refused"));

        RedisHealthValidator validator = new RedisHealthValidator(connectionFactory, environment);

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                .isInstanceOf(InvalidConfigurationException.class);
    }
}
