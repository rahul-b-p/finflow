package com.finflow.core.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class AuditRecord {

    private Long auditId;
    private UUID id;
    private String entityType;
    private String revType;
    private Map<String, Object> diffData;
    private String modifiedBy;
    private String ipAddress;
    private Instant revTimestamp;

    public AuditRecord() {
    }

    public AuditRecord(UUID id, String entityType, String revType, Map<String, Object> diffData, String modifiedBy, String ipAddress, Instant revTimestamp) {
        this.id = id;
        this.entityType = entityType;
        this.revType = revType;
        this.diffData = diffData;
        this.modifiedBy = modifiedBy;
        this.ipAddress = ipAddress;
        this.revTimestamp = revTimestamp != null ? revTimestamp : Instant.now();
    }

    public Long getAuditId() {
        return auditId;
    }

    public void setAuditId(Long auditId) {
        this.auditId = auditId;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getRevType() {
        return revType;
    }

    public void setRevType(String revType) {
        this.revType = revType;
    }

    public Map<String, Object> getDiffData() {
        return diffData;
    }

    public void setDiffData(Map<String, Object> diffData) {
        this.diffData = diffData;
    }

    public String getModifiedBy() {
        return modifiedBy;
    }

    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Instant getRevTimestamp() {
        return revTimestamp;
    }

    public void setRevTimestamp(Instant revTimestamp) {
        this.revTimestamp = revTimestamp;
    }
}
