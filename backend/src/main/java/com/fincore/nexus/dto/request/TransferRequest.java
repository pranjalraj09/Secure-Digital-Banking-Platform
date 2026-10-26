package com.fincore.nexus.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(
        UUID sourceAccountId,
        String sourceAccountNumber,
        UUID targetAccountId,
        String targetAccountNumber,
        @NotNull(message = "Transfer amount is required.")
        @DecimalMin(value = "0.01", message = "Transfer amount must be strictly greater than zero.")
        BigDecimal amount,
        @Size(max = 255, message = "Description cannot exceed 255 characters.")
        String description,
        String referenceId
) {
}
