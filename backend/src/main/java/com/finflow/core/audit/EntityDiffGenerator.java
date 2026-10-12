package com.finflow.core.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
public class EntityDiffGenerator {

    private static final Logger log = LoggerFactory.getLogger(EntityDiffGenerator.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "passwordhash", "password_hash",
            "email", "phone", "phonenumber", "firstname", "first_name",
            "lastname", "last_name"
    );

    public Map<String, Object> generateInsertDiff(Object entity) {
        Map<String, Object> diff = new HashMap<>();
        if (entity == null) {
            return diff;
        }

        Field[] fields = entity.getClass().getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            try {
                String fieldName = field.getName();
                Object value = field.get(entity);
                if (value != null) {
                    Object sanitizedValue = sanitizeValue(fieldName, value);
                    Map<String, Object> change = new HashMap<>();
                    change.put("old", null);
                    change.put("new", sanitizedValue);
                    diff.put(fieldName, change);
                }
            } catch (IllegalAccessException e) {
                log.warn("Failed to access field: {}", field.getName(), e);
            }
        }
        return diff;
    }

    public String toJsonString(Map<String, Object> diff) {
        try {
            return OBJECT_MAPPER.writeValueAsString(diff);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize diff map to JSON", e);
            return "{}";
        }
    }

    private Object sanitizeValue(String fieldName, Object value) {
        String lower = fieldName.toLowerCase().replace("_", "");
        if (lower.contains("password")) {
            return "[PASSWORD_MASKED]";
        }
        if (lower.contains("email") || lower.contains("phone") || lower.contains("name")) {
            return "[PII_MASKED]";
        }
        return value;
    }
}
