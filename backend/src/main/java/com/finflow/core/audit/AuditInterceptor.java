package com.finflow.core.audit;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.UUID;

public class AuditInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuditInterceptor.class);

    private static volatile EntityAuditService auditService;
    private static volatile EntityDiffGenerator diffGenerator;

    public static void registerServices(EntityAuditService service, EntityDiffGenerator generator) {
        auditService = service;
        diffGenerator = generator;
    }

    @PostPersist
    public void onPostPersist(Object entity) {
        handleAudit(entity, "INSERT");
    }

    @PostUpdate
    public void onPostUpdate(Object entity) {
        handleAudit(entity, "UPDATE");
    }

    @PostRemove
    public void onPostRemove(Object entity) {
        handleAudit(entity, "DELETE");
    }

    private void handleAudit(Object entity, String revType) {
        if (auditService == null || diffGenerator == null) {
            log.trace("Audit services not yet initialized; skipping audit interceptor");
            return;
        }

        Auditable auditable = entity.getClass().getAnnotation(Auditable.class);
        if (auditable == null || !(entity instanceof AuditableEntity auditableEntity)) {
            return;
        }

        UUID entityId = auditableEntity.getId();
        if (entityId == null) {
            return;
        }

        String tableName = auditable.tableName();
        String actor = resolveActor();
        String ip = MDC.get("clientIp");

        Map<String, Object> diffData = diffGenerator.generateInsertDiff(entity);

        try {
            auditService.recordMutation(tableName, entityId, revType, diffData, actor, ip);
        } catch (Exception e) {
            log.error("Audit logging failed during {} on {} id={}", revType, tableName, entityId, e);
            throw e;
        }
    }

    private String resolveActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        String correlationActor = MDC.get("actor");
        if (correlationActor != null && !correlationActor.isBlank()) {
            return correlationActor;
        }
        return "SYSTEM_BOOTSTRAP";
    }
}
