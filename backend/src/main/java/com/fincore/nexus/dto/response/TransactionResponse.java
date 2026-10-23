package com.fincore.nexus.dto.response;

import com.fincore.nexus.entity.Transaction;
import com.fincore.nexus.entity.TransactionStatus;
import com.fincore.nexus.entity.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID accountId,
        String accountNumber,
        TransactionType transactionType,
        BigDecimal amount,
        BigDecimal previousBalance,
        BigDecimal resultingBalance,
        TransactionStatus status,
        String description,
        String referenceId,
        String failureReason,
        Instant timestamp
) {
    public static TransactionResponse fromEntity(Transaction txn) {
        return new TransactionResponse(
                txn.getId(),
                txn.getAccount() != null ? txn.getAccount().getId() : null,
                txn.getAccount() != null ? txn.getAccount().getAccountNumber() : null,
                txn.getTransactionType(),
                txn.getAmount() != null ? txn.getAmount().setScale(2, RoundingMode.HALF_EVEN) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN),
                txn.getPreviousBalance() != null ? txn.getPreviousBalance().setScale(2, RoundingMode.HALF_EVEN) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN),
                txn.getResultingBalance() != null ? txn.getResultingBalance().setScale(2, RoundingMode.HALF_EVEN) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN),
                txn.getStatus(),
                txn.getDescription(),
                txn.getReferenceId(),
                txn.getFailureReason(),
                txn.getTimestamp()
        );
    }
}
