package com.fincore.nexus.dto.response;

import com.fincore.nexus.entity.Account;
import com.fincore.nexus.entity.AccountStatus;
import com.fincore.nexus.entity.AccountType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String accountNumber,
        UUID customerId,
        String customerName,
        AccountType accountType,
        String currency,
        BigDecimal balance,
        AccountStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static AccountResponse fromEntity(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getCustomer() != null ? account.getCustomer().getId() : null,
                account.getCustomer() != null ? account.getCustomer().getFullName() : null,
                account.getAccountType(),
                account.getCurrency(),
                account.getBalance() != null ? account.getBalance().setScale(2, RoundingMode.HALF_EVEN) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN),
                account.getStatus(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
