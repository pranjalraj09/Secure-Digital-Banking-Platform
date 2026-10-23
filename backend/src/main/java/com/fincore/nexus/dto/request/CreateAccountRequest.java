package com.fincore.nexus.dto.request;

import com.fincore.nexus.entity.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateAccountRequest(
        @NotNull(message = "Customer ID is required")
        UUID customerId,

        @NotNull(message = "Account type is required (SAVINGS or CHECKING)")
        AccountType accountType,

        @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code (e.g. USD)")
        String currency,

        @DecimalMin(value = "0.00", message = "Initial balance cannot be negative")
        BigDecimal initialBalance
) {
}
