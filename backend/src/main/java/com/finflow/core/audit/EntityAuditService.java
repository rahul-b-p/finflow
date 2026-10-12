package com.finflow.core.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class EntityAuditService {

    private static final Logger log = LoggerFactory.getLogger(EntityAuditService.class);

    private final JdbcTemplate jdbcTemplate;
    private final EntityDiffGenerator diffGenerator;

    public EntityAuditService(JdbcTemplate jdbcTemplate, EntityDiffGenerator diffGenerator) {
        this.jdbcTemplate = jdbcTemplate;
        this.diffGenerator = diffGenerator;
        AuditInterceptor.registerServices(this, diffGenerator);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordMutation(String tableName, UUID entityId, String revType, Map<String, Object> diffData, String actor, String ip) {
        String sql = String.format(
                "INSERT INTO %s_aud (id, rev_type, diff_data, modified_by, ip_address, rev_timestamp) VALUES (?, ?, ?, ?, ?, ?)",
                tableName
        );

        String jsonDiff = diffGenerator.toJsonString(diffData);
        Timestamp now = Timestamp.from(Instant.now());

        try {
            jdbcTemplate.update(sql, entityId, revType, jsonDiff, actor != null ? actor : "SYSTEM_BOOTSTRAP", ip, now);
            log.debug("Recorded audit record for {} id={} revType={}", tableName, entityId, revType);
        } catch (Exception e) {
            log.error("Failed to write shadow audit record to {}_aud for entity {}", tableName, entityId, e);
            throw e;
        }
    }
}
