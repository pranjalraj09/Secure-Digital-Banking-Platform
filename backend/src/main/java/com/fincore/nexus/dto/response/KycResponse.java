package com.fincore.nexus.dto.response;

import com.fincore.nexus.entity.Customer;
import com.fincore.nexus.entity.KycStatus;
import com.fincore.nexus.entity.RiskLevel;

import java.time.Instant;
import java.util.UUID;

public record KycResponse(
        UUID customerId,
        String customerName,
        KycStatus kycStatus,
        String kycNotes,
        RiskLevel riskLevel,
        Instant updatedAt
) {
    public static KycResponse fromEntity(Customer customer) {
        return new KycResponse(
                customer.getId(),
                customer.getFullName(),
                customer.getKycStatus(),
                customer.getKycNotes(),
                customer.getRiskLevel(),
                customer.getUpdatedAt()
        );
    }
}
