package com.fincore.nexus.dto.response;

import com.fincore.nexus.entity.Account;
import com.fincore.nexus.entity.AccountStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

public record AccountBalanceResponse(
        UUID accountId,
        String accountNumber,
        BigDecimal balance,
        String currency,
        AccountStatus status,
        boolean cached,
        Instant retrievedAt
) implements Serializable {
    public static AccountBalanceResponse fromEntity(Account account, boolean cached) {
        return new AccountBalanceResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getBalance() != null ? account.getBalance().setScale(2, RoundingMode.HALF_EVEN) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN),
                account.getCurrency(),
                account.getStatus(),
                cached,
                Instant.now()
        );
    }
}
