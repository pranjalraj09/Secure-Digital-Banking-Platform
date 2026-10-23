package com.fincore.nexus;

import com.fincore.nexus.dto.response.AuditLogResponse;
import com.fincore.nexus.entity.AuditLog;
import com.fincore.nexus.repository.AuditLogRepository;
import com.fincore.nexus.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(auditLogRepository);
    }

    @Test
    @DisplayName("Should persist immutable audit log record")
    void testLogAction() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuditLog log = auditService.logAction("admin", "ACCOUNT_CREATE", "ACCOUNT", "acc-101", "SUCCESS", "127.0.0.1", "metadata");

        assertThat(log).isNotNull();
        assertThat(log.getActor()).isEqualTo("admin");
        assertThat(log.getAction()).isEqualTo("ACCOUNT_CREATE");
        assertThat(log.getOutcome()).isEqualTo("SUCCESS");

        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("Should query audit logs by actor")
    void testQueryAuditLogsByActor() {
        AuditLog log = new AuditLog(UUID.randomUUID(), "teller@fincore.bank", "DEPOSIT", "ACCOUNT", "acc-202", "SUCCESS", "127.0.0.1", "{}");
        when(auditLogRepository.findByActorOrderByTimestampDesc(eq("teller@fincore.bank"), any()))
                .thenReturn(new PageImpl<>(List.of(log), PageRequest.of(0, 10), 1));

        Page<AuditLogResponse> result = auditService.getAuditLogs(null, null, "teller@fincore.bank", PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).actor()).isEqualTo("teller@fincore.bank");
    }
}
