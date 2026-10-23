package com.fincore.nexus.service;

import com.fincore.nexus.dto.request.DepositRequest;
import com.fincore.nexus.dto.request.WithdrawRequest;
import com.fincore.nexus.dto.response.TransactionResponse;
import com.fincore.nexus.entity.Account;
import com.fincore.nexus.entity.AccountStatus;
import com.fincore.nexus.entity.IdempotencyRecord;
import com.fincore.nexus.entity.Transaction;
import com.fincore.nexus.entity.TransactionStatus;
import com.fincore.nexus.entity.TransactionType;
import com.fincore.nexus.event.TransactionCompletedEvent;
import com.fincore.nexus.exception.AccountClosedException;
import com.fincore.nexus.exception.AccountFrozenException;
import com.fincore.nexus.exception.InsufficientFundsException;
import com.fincore.nexus.exception.InvalidAmountException;
import com.fincore.nexus.exception.ResourceNotFoundException;
import com.fincore.nexus.repository.AccountRepository;
import com.fincore.nexus.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final AuditService auditService;
    private final IdempotencyService idempotencyService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;

    public TransactionService(AccountRepository accountRepository,
                              TransactionRepository transactionRepository,
                              AccountService accountService,
                              AuditService auditService,
                              IdempotencyService idempotencyService,
                              ApplicationEventPublisher applicationEventPublisher,
                              ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.accountService = accountService;
        this.auditService = auditService;
        this.idempotencyService = idempotencyService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TransactionResponse deposit(UUID accountId, DepositRequest req, String idempotencyKey, String actor, String ip) {
        // Idempotency check
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<IdempotencyRecord> existing = idempotencyService.getRecord(idempotencyKey);
            if (existing.isPresent()) {
                idempotencyService.validateRequestHash(existing.get(), req);
                try {
                    log.info("Returning cached idempotent deposit response for key: {}", idempotencyKey);
                    return objectMapper.readValue(existing.get().getResponseBody(), TransactionResponse.class);
                } catch (Exception ex) {
                    log.warn("Failed to deserialize cached idempotency response: {}", ex.getMessage());
                }
            }
        }

        validateAmount(req.amount());

        // Pessimistic lock on the account to coordinate safe concurrent balance mutations
        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + accountId));

        validateAccountOperable(account, "deposit", actor, ip);

        BigDecimal normalizedAmount = req.amount().setScale(4, RoundingMode.HALF_EVEN);
        BigDecimal previousBalance = account.getBalance();
        BigDecimal newBalance = previousBalance.add(normalizedAmount);

        // Mutate and persist Account
        account.setBalance(newBalance);
        accountRepository.save(account);

        // Generate durable transaction record
        String refId = (req.referenceId() != null && !req.referenceId().isBlank())
                ? req.referenceId()
                : "DEP-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);

        Transaction transaction = new Transaction(
                UUID.randomUUID(),
                account,
                TransactionType.DEPOSIT,
                normalizedAmount,
                previousBalance,
                newBalance,
                TransactionStatus.COMPLETED,
                req.description() != null ? req.description() : "Cash Deposit",
                refId
        );

        Transaction savedTxn = transactionRepository.save(transaction);

        // Evict cache to guarantee fresh reads
        accountService.evictBalanceCache(accountId);

        // Publish event for Kafka (dispatches strictly AFTER_COMMIT)
        TransactionCompletedEvent event = new TransactionCompletedEvent(
                savedTxn.getId(),
                account.getId(),
                account.getAccountNumber(),
                savedTxn.getTransactionType(),
                savedTxn.getAmount().setScale(2, RoundingMode.HALF_EVEN),
                savedTxn.getResultingBalance().setScale(2, RoundingMode.HALF_EVEN),
                savedTxn.getStatus(),
                savedTxn.getReferenceId(),
                savedTxn.getTimestamp()
        );
        applicationEventPublisher.publishEvent(event);

        // Log audit
        auditService.logAction(actor, "DEPOSIT", "ACCOUNT", accountId.toString(), "SUCCESS", ip,
                String.format("Deposited %s %s into account %s (ref: %s)",
                        savedTxn.getAmount().setScale(2, RoundingMode.HALF_EVEN),
                        account.getCurrency(),
                        account.getAccountNumber(),
                        refId));

        TransactionResponse response = TransactionResponse.fromEntity(savedTxn);

        // Save idempotency record
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyService.saveRecord(idempotencyKey, req, 200, response);
        }

        return response;
    }

    @Transactional
    public TransactionResponse withdraw(UUID accountId, WithdrawRequest req, String idempotencyKey, String actor, String ip) {
        // Idempotency check
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<IdempotencyRecord> existing = idempotencyService.getRecord(idempotencyKey);
            if (existing.isPresent()) {
                idempotencyService.validateRequestHash(existing.get(), req);
                try {
                    log.info("Returning cached idempotent withdrawal response for key: {}", idempotencyKey);
                    return objectMapper.readValue(existing.get().getResponseBody(), TransactionResponse.class);
                } catch (Exception ex) {
                    log.warn("Failed to deserialize cached idempotency response: {}", ex.getMessage());
                }
            }
        }

        validateAmount(req.amount());

        // Pessimistic lock on the account to coordinate safe concurrent balance mutations
        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + accountId));

        validateAccountOperable(account, "withdraw", actor, ip);

        BigDecimal normalizedAmount = req.amount().setScale(4, RoundingMode.HALF_EVEN);
        BigDecimal currentBalance = account.getBalance();

        // Enforce balance sufficiency
        if (currentBalance.compareTo(normalizedAmount) < 0) {
            String reason = String.format("Insufficient funds: Requested %s %s, available balance %s %s",
                    normalizedAmount.setScale(2, RoundingMode.HALF_EVEN),
                    account.getCurrency(),
                    currentBalance.setScale(2, RoundingMode.HALF_EVEN),
                    account.getCurrency());

            auditService.logAction(actor, "WITHDRAWAL_REJECTED", "ACCOUNT", accountId.toString(), "FAILURE", ip, reason);
            throw new InsufficientFundsException(reason);
        }

        BigDecimal newBalance = currentBalance.subtract(normalizedAmount);

        // Mutate and persist Account
        account.setBalance(newBalance);
        accountRepository.save(account);

        // Generate durable transaction record
        String refId = (req.referenceId() != null && !req.referenceId().isBlank())
                ? req.referenceId()
                : "WTH-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);

        Transaction transaction = new Transaction(
                UUID.randomUUID(),
                account,
                TransactionType.WITHDRAWAL,
                normalizedAmount,
                currentBalance,
                newBalance,
                TransactionStatus.COMPLETED,
                req.description() != null ? req.description() : "Cash Withdrawal",
                refId
        );

        Transaction savedTxn = transactionRepository.save(transaction);

        // Evict cache to guarantee fresh reads
        accountService.evictBalanceCache(accountId);

        // Publish event for Kafka (dispatches strictly AFTER_COMMIT)
        TransactionCompletedEvent event = new TransactionCompletedEvent(
                savedTxn.getId(),
                account.getId(),
                account.getAccountNumber(),
                savedTxn.getTransactionType(),
                savedTxn.getAmount().setScale(2, RoundingMode.HALF_EVEN),
                savedTxn.getResultingBalance().setScale(2, RoundingMode.HALF_EVEN),
                savedTxn.getStatus(),
                savedTxn.getReferenceId(),
                savedTxn.getTimestamp()
        );
        applicationEventPublisher.publishEvent(event);

        // Log audit
        auditService.logAction(actor, "WITHDRAWAL", "ACCOUNT", accountId.toString(), "SUCCESS", ip,
                String.format("Withdrew %s %s from account %s (ref: %s)",
                        savedTxn.getAmount().setScale(2, RoundingMode.HALF_EVEN),
                        account.getCurrency(),
                        account.getAccountNumber(),
                        refId));

        TransactionResponse response = TransactionResponse.fromEntity(savedTxn);

        // Save idempotency record
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyService.saveRecord(idempotencyKey, req, 200, response);
        }

        return response;
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(UUID accountId, Pageable pageable) {
        if (!accountRepository.existsById(accountId)) {
            throw new ResourceNotFoundException("Account not found with ID: " + accountId);
        }
        return transactionRepository.findByAccountIdOrderByTimestampDesc(accountId, pageable)
                .map(TransactionResponse::fromEntity);
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new InvalidAmountException("Transaction amount is required.");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Transaction amount must be strictly greater than zero.");
        }
        if (amount.scale() > 2) {
            throw new InvalidAmountException("Transaction amount cannot have more than 2 decimal places.");
        }
    }

    private void validateAccountOperable(Account account, String operation, String actor, String ip) {
        if (account.getStatus() == AccountStatus.FROZEN) {
            auditService.logAction(actor, operation.toUpperCase() + "_REJECTED_FROZEN", "ACCOUNT",
                    account.getId().toString(), "FAILURE", ip, "Account is frozen");
            throw new AccountFrozenException("Cannot perform " + operation + " on frozen account: " + account.getAccountNumber());
        }
        if (account.getStatus() == AccountStatus.CLOSED) {
            auditService.logAction(actor, operation.toUpperCase() + "_REJECTED_CLOSED", "ACCOUNT",
                    account.getId().toString(), "FAILURE", ip, "Account is closed");
            throw new AccountClosedException("Cannot perform " + operation + " on closed account: " + account.getAccountNumber());
        }
    }
}
