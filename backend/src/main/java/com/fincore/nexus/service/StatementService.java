package com.fincore.nexus.service;

import com.fincore.nexus.dto.response.AccountStatementResponse;
import com.fincore.nexus.dto.response.TransactionResponse;
import com.fincore.nexus.entity.Account;
import com.fincore.nexus.entity.Transaction;
import com.fincore.nexus.entity.TransactionType;
import com.fincore.nexus.exception.ResourceNotFoundException;
import com.fincore.nexus.repository.AccountRepository;
import com.fincore.nexus.repository.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class StatementService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public StatementService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public AccountStatementResponse generateStatement(UUID accountId, Instant startDate, Instant endDate, Pageable pageable) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + accountId));

        Instant effectiveEnd = endDate != null ? endDate : Instant.now();
        Instant effectiveStart = startDate != null ? startDate : effectiveEnd.minus(30, ChronoUnit.DAYS);

        if (effectiveStart.isAfter(effectiveEnd)) {
            throw new IllegalArgumentException("startDate cannot be after endDate.");
        }

        Page<Transaction> txnPage = transactionRepository.findByAccountIdAndTimestampBetweenOrderByTimestampDesc(
                accountId, effectiveStart, effectiveEnd, pageable);

        List<TransactionResponse> txnResponses = txnPage.getContent().stream()
                .map(TransactionResponse::fromEntity)
                .toList();

        // Calculate totals across bounded query
        BigDecimal totalDeposits = BigDecimal.ZERO;
        BigDecimal totalWithdrawals = BigDecimal.ZERO;

        for (Transaction txn : txnPage.getContent()) {
            if (txn.getTransactionType() == TransactionType.DEPOSIT) {
                totalDeposits = totalDeposits.add(txn.getAmount());
            } else if (txn.getTransactionType() == TransactionType.WITHDRAWAL) {
                totalWithdrawals = totalWithdrawals.add(txn.getAmount());
            }
        }

        BigDecimal openingBalance = BigDecimal.ZERO;
        BigDecimal closingBalance = account.getBalance();

        if (!txnPage.getContent().isEmpty()) {
            // Because ordered by timestamp DESC, the last element in list is the earliest in the window
            Transaction earliest = txnPage.getContent().get(txnPage.getContent().size() - 1);
            openingBalance = earliest.getPreviousBalance();

            // First element in list is the latest in the window
            Transaction latest = txnPage.getContent().get(0);
            closingBalance = latest.getResultingBalance();
        }

        return new AccountStatementResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getCustomer() != null ? account.getCustomer().getFullName() : "N/A",
                effectiveStart,
                effectiveEnd,
                openingBalance.setScale(2, RoundingMode.HALF_EVEN),
                closingBalance.setScale(2, RoundingMode.HALF_EVEN),
                totalDeposits.setScale(2, RoundingMode.HALF_EVEN),
                totalWithdrawals.setScale(2, RoundingMode.HALF_EVEN),
                (int) txnPage.getTotalElements(),
                txnResponses,
                Instant.now()
        );
    }
}
