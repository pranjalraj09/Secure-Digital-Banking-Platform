package com.fincore.nexus.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AccountStatementResponse(
        UUID accountId,
        String accountNumber,
        String customerName,
        Instant startDate,
        Instant endDate,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        BigDecimal totalDeposits,
        BigDecimal totalWithdrawals,
        int transactionCount,
        List<TransactionResponse> transactions,
        Instant generatedAt
) {
}
