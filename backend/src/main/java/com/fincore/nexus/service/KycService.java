package com.fincore.nexus.service;

import com.fincore.nexus.dto.request.KycUpdateRequest;
import com.fincore.nexus.dto.response.KycResponse;
import com.fincore.nexus.entity.Customer;
import com.fincore.nexus.entity.KycStatus;
import com.fincore.nexus.exception.ResourceNotFoundException;
import com.fincore.nexus.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class KycService {

    private static final Logger log = LoggerFactory.getLogger(KycService.class);

    private final CustomerRepository customerRepository;
    private final AuditService auditService;

    public KycService(CustomerRepository customerRepository, AuditService auditService) {
        this.customerRepository = customerRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public KycResponse getKycStatus(UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));
        return KycResponse.fromEntity(customer);
    }

    @Transactional
    public KycResponse updateKycStatus(UUID customerId, KycUpdateRequest req, String actor, String ip) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));

        KycStatus oldStatus = customer.getKycStatus();
        customer.setKycStatus(req.status());
        if (req.notes() != null && !req.notes().isBlank()) {
            customer.setKycNotes(req.notes());
        }
        if (req.riskLevel() != null) {
            customer.setRiskLevel(req.riskLevel());
        }

        Customer updated = customerRepository.save(customer);

        String metadata = String.format("{\"oldStatus\":\"%s\",\"newStatus\":\"%s\",\"riskLevel\":\"%s\"}",
                oldStatus, req.status(), updated.getRiskLevel());

        auditService.logAction(actor, "KYC_STATUS_UPDATE", "CUSTOMER", customerId.toString(), "SUCCESS", ip, metadata);

        log.info("KYC status updated for customer {}: {} -> {}", customerId, oldStatus, req.status());
        return KycResponse.fromEntity(updated);
    }
}
