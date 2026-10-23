package com.fincore.nexus.service;

import com.fincore.nexus.dto.response.AuditLogResponse;
import com.fincore.nexus.entity.AuditLog;
import com.fincore.nexus.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog logAction(String actor, String action, String resourceType, String resourceId,
                              String outcome, String ipAddress, String metadata) {
        try {
            AuditLog auditLog = new AuditLog(
                    UUID.randomUUID(),
                    actor != null ? actor : "SYSTEM",
                    action,
                    resourceType,
                    resourceId,
                    outcome,
                    ipAddress,
                    metadata
            );
            AuditLog saved = auditLogRepository.save(auditLog);
            log.info("AUDIT LOG: [Actor: {}] [Action: {}] [Resource: {}:{}] [Outcome: {}]",
                    saved.getActor(), saved.getAction(), saved.getResourceType(), saved.getResourceId(), saved.getOutcome());
            return saved;
        } catch (Exception ex) {
            log.error("Failed to write audit log: ", ex);
            return null;
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAuditLogs(String resourceType, String resourceId, String actor, Pageable pageable) {
        if (resourceType != null && resourceId != null) {
            return auditLogRepository.findByResourceTypeAndResourceIdOrderByTimestampDesc(resourceType, resourceId, pageable)
                    .map(AuditLogResponse::fromEntity);
        }
        if (actor != null && !actor.isBlank()) {
            return auditLogRepository.findByActorOrderByTimestampDesc(actor, pageable)
                    .map(AuditLogResponse::fromEntity);
        }
        return auditLogRepository.findAllByOrderByTimestampDesc(pageable)
                .map(AuditLogResponse::fromEntity);
    }
}
