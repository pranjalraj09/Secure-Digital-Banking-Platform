package com.fincore.nexus.dto.response;

import com.fincore.nexus.entity.AuditLog;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        String actor,
        String action,
        String resourceType,
        String resourceId,
        String outcome,
        String ipAddress,
        String metadata,
        Instant timestamp
) {
    public static AuditLogResponse fromEntity(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getActor(),
                log.getAction(),
                log.getResourceType(),
                log.getResourceId(),
                log.getOutcome(),
                log.getIpAddress(),
                log.getMetadata(),
                log.getTimestamp()
        );
    }
}
