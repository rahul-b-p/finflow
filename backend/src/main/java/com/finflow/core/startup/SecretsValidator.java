package com.finflow.core.startup;

import com.finflow.core.crypto.CryptoUtils;
import com.finflow.core.exception.InvalidConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;

@Component
@Order(1)
public class SecretsValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SecretsValidator.class);
    private static final String DEFAULT_DEV_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String DEFAULT_DEV_JWT = "dGhpc2lzYXZlcnlzZWNyZXRrZXlmb3Jqd3RzaWduaW5nMTIzNDU=";

    private final Environment environment;

    public SecretsValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        boolean isStrict = activeProfiles.contains("staging") || activeProfiles.contains("production");

        String piiKey = environment.getProperty("finflow.security.pii-encryption-key");
        String jwtSecret = environment.getProperty("finflow.security.jwt-secret");

        if (piiKey == null || piiKey.isBlank()) {
            if (isStrict) {
                throw new InvalidConfigurationException("PII_ENCRYPTION_KEY must be provided in staging and production");
            } else {
                log.warn("PII_ENCRYPTION_KEY not set; using fallback test/dev key");
                piiKey = DEFAULT_DEV_KEY;
            }
        }

        try {
            byte[] decodedKey = Base64.getDecoder().decode(piiKey.trim());
            if (decodedKey.length != 32) {
                throw new InvalidConfigurationException("PII_ENCRYPTION_KEY must be 32 bytes (256-bit AES key), actual: " + decodedKey.length);
            }
            CryptoUtils.init(decodedKey);
        } catch (IllegalArgumentException e) {
            throw new InvalidConfigurationException("PII_ENCRYPTION_KEY is not valid Base64", e);
        }

        if (isStrict) {
            if (DEFAULT_DEV_KEY.equals(piiKey)) {
                throw new InvalidConfigurationException("Default development PII_ENCRYPTION_KEY cannot be used in staging or production");
            }
            if (jwtSecret == null || jwtSecret.isBlank() || DEFAULT_DEV_JWT.equals(jwtSecret)) {
                throw new InvalidConfigurationException("A secure non-default JWT_SECRET must be configured in staging and production");
            }
        } else {
            if (DEFAULT_DEV_KEY.equals(piiKey)) {
                log.info("Running with development default PII_ENCRYPTION_KEY");
            }
        }

        log.info("Startup secrets validation completed successfully [strict={}]", isStrict);
    }
}
