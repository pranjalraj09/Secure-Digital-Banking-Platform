package com.fincore.nexus.dto.response;

import com.fincore.nexus.entity.Transaction;
import com.fincore.nexus.entity.TransactionStatus;
import com.fincore.nexus.entity.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        UUID accountId,
        UUID sourceAccountId,
        String accountNumber,
        String sourceAccountNumber,
        UUID targetAccountId,
        String targetAccountNumber,
        TransactionType transactionType,
        BigDecimal amount,
        BigDecimal previousBalance,
        BigDecimal sourcePreviousBalance,
        BigDecimal resultingBalance,
        BigDecimal sourceResultingBalance,
        BigDecimal targetPreviousBalance,
        BigDecimal targetResultingBalance,
        TransactionStatus status,
        String description,
        String referenceId,
        String failureReason,
        Instant timestamp
) {
    public static TransferResponse of(Transaction debitTxn, Transaction creditTxn) {
        BigDecimal normalizedAmount = debitTxn.getAmount() != null
                ? debitTxn.getAmount().setScale(2, RoundingMode.HALF_EVEN)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);

        BigDecimal srcPrev = debitTxn.getPreviousBalance() != null
                ? debitTxn.getPreviousBalance().setScale(2, RoundingMode.HALF_EVEN)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);

        BigDecimal srcResult = debitTxn.getResultingBalance() != null
                ? debitTxn.getResultingBalance().setScale(2, RoundingMode.HALF_EVEN)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);

        BigDecimal tgtPrev = creditTxn.getPreviousBalance() != null
                ? creditTxn.getPreviousBalance().setScale(2, RoundingMode.HALF_EVEN)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);

        BigDecimal tgtResult = creditTxn.getResultingBalance() != null
                ? creditTxn.getResultingBalance().setScale(2, RoundingMode.HALF_EVEN)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);

        return new TransferResponse(
                debitTxn.getId(),
                debitTxn.getAccount().getId(),
                debitTxn.getAccount().getId(),
                debitTxn.getAccount().getAccountNumber(),
                debitTxn.getAccount().getAccountNumber(),
                creditTxn.getAccount().getId(),
                creditTxn.getAccount().getAccountNumber(),
                TransactionType.TRANSFER,
                normalizedAmount,
                srcPrev,
                srcPrev,
                srcResult,
                srcResult,
                tgtPrev,
                tgtResult,
                debitTxn.getStatus(),
                debitTxn.getDescription(),
                debitTxn.getReferenceId(),
                debitTxn.getFailureReason(),
                debitTxn.getTimestamp()
        );
    }
}
