package com.finflow.core.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PiiMaskingPatternLayout extends MessageConverter {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(\\+?\\d{1,3}[-.\\s]?)?(\\(?\\d{3}\\)?[-.\\s]?)?\\d{3}[-.\\s]?\\d{4}"
    );

    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile(
            "Bearer\\s+[A-Za-z0-9\\-._~+/]+=*",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PASSWORD_JSON_PATTERN = Pattern.compile(
            "\"password\"\\s*:\\s*\"[^\"]+\"",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public String convert(ILoggingEvent event) {
        String in = super.convert(event);
        if (in == null || in.isEmpty()) {
            return in;
        }

        String masked = in;

        Matcher bearerMatcher = BEARER_TOKEN_PATTERN.matcher(masked);
        if (bearerMatcher.find()) {
            masked = bearerMatcher.replaceAll("Bearer [TOKEN_MASKED]");
        }

        Matcher passwordMatcher = PASSWORD_JSON_PATTERN.matcher(masked);
        if (passwordMatcher.find()) {
            masked = passwordMatcher.replaceAll("\"password\":\"[PASSWORD_MASKED]\"");
        }

        Matcher emailMatcher = EMAIL_PATTERN.matcher(masked);
        if (emailMatcher.find()) {
            masked = emailMatcher.replaceAll("[EMAIL_MASKED]");
        }

        Matcher phoneMatcher = PHONE_PATTERN.matcher(masked);
        if (phoneMatcher.find()) {
            masked = phoneMatcher.replaceAll("[PHONE_MASKED]");
        }

        return masked;
    }
}
