package com.fincore.nexus.event;

import com.fincore.nexus.entity.TransactionStatus;
import com.fincore.nexus.entity.TransactionType;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionCompletedEvent(
        UUID transactionId,
        UUID accountId,
        String accountNumber,
        TransactionType transactionType,
        BigDecimal amount,
        BigDecimal resultingBalance,
        TransactionStatus status,
        String referenceId,
        Instant timestamp
) implements Serializable {
}
