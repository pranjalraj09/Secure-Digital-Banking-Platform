package com.fincore.nexus.dto.request;

import com.fincore.nexus.entity.AccountStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateAccountStatusRequest(
        @NotNull(message = "New account status is required (ACTIVE, FROZEN, CLOSED)")
        AccountStatus status,

        @Size(max = 255, message = "Reason must not exceed 255 characters")
        String reason
) {
}
