package com.fincore.nexus.dto.request;

import com.fincore.nexus.entity.KycStatus;
import com.fincore.nexus.entity.RiskLevel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record KycUpdateRequest(
        @NotNull(message = "KYC status is required (PENDING, VERIFIED, REJECTED)")
        KycStatus status,

        @Size(max = 1000, message = "Notes must not exceed 1000 characters")
        String notes,

        RiskLevel riskLevel
) {
}
