package com.fincore.nexus.service;

import com.fincore.nexus.dto.request.CreateAccountRequest;
import com.fincore.nexus.dto.request.UpdateAccountStatusRequest;
import com.fincore.nexus.dto.response.AccountBalanceResponse;
import com.fincore.nexus.dto.response.AccountResponse;
import com.fincore.nexus.entity.Account;
import com.fincore.nexus.entity.AccountStatus;
import com.fincore.nexus.entity.Customer;
import com.fincore.nexus.entity.Transaction;
import com.fincore.nexus.entity.TransactionStatus;
import com.fincore.nexus.entity.TransactionType;
import com.fincore.nexus.exception.InvalidAccountStateTransitionException;
import com.fincore.nexus.exception.InvalidAmountException;
import com.fincore.nexus.exception.ResourceNotFoundException;
import com.fincore.nexus.repository.AccountRepository;
import com.fincore.nexus.repository.CustomerRepository;
import com.fincore.nexus.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    public static final String BALANCE_CACHE_NAME = "account_balances";

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;
    private final Optional<CacheManager> cacheManager;
    private final SecureRandom secureRandom = new SecureRandom();

    public AccountService(AccountRepository accountRepository,
                          CustomerRepository customerRepository,
                          TransactionRepository transactionRepository,
                          AuditService auditService,
                          Optional<CacheManager> cacheManager) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
        this.auditService = auditService;
        this.cacheManager = cacheManager;
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest req, String actor, String ip) {
        Customer customer = customerRepository.findById(req.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + req.customerId()));

        String accountNumber = generateUniqueAccountNumber();
        String currency = (req.currency() != null && !req.currency().isBlank()) ? req.currency().toUpperCase() : "USD";

        BigDecimal initialDeposit = req.initialBalance() != null ? req.initialBalance() : BigDecimal.ZERO;
        if (initialDeposit.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidAmountException("Initial balance cannot be negative");
        }

        Account account = new Account(
                UUID.randomUUID(),
                accountNumber,
                customer,
                req.accountType(),
                currency,
                BigDecimal.ZERO
        );

        Account saved = accountRepository.save(account);

        // If an initial deposit was supplied, record it atomically as the first transaction
        if (initialDeposit.compareTo(BigDecimal.ZERO) > 0) {
            saved.setBalance(initialDeposit);
            saved = accountRepository.save(saved);

            Transaction initialTxn = new Transaction(
                    UUID.randomUUID(),
                    saved,
                    TransactionType.DEPOSIT,
                    initialDeposit,
                    BigDecimal.ZERO,
                    initialDeposit,
                    TransactionStatus.COMPLETED,
                    "Initial opening balance deposit",
                    "INIT-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6)
            );
            transactionRepository.save(initialTxn);
        }

        auditService.logAction(actor, "ACCOUNT_CREATE", "ACCOUNT", saved.getId().toString(), "SUCCESS", ip,
                String.format("Created %s account %s for customer %s with initial balance %s",
                        saved.getAccountType(), saved.getAccountNumber(), customer.getFullName(), saved.getBalance()));

        return AccountResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountById(UUID id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + id));
        return AccountResponse.fromEntity(account);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountByNumber(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with number: " + accountNumber));
        return AccountResponse.fromEntity(account);
    }

    @Transactional(readOnly = true)
    public Page<AccountResponse> listAccounts(Pageable pageable) {
        return accountRepository.findAll(pageable)
                .map(AccountResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsByCustomerId(UUID customerId) {
        return accountRepository.findByCustomerId(customerId)
                .stream()
                .map(AccountResponse::fromEntity)
                .toList();
    }

    @Transactional
    public AccountResponse updateAccountStatus(UUID id, UpdateAccountStatusRequest req, String actor, String ip) {
        Account account = accountRepository.findByIdWithLock(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + id));

        AccountStatus currentStatus = account.getStatus();
        AccountStatus targetStatus = req.status();

        if (currentStatus == targetStatus) {
            return AccountResponse.fromEntity(account);
        }

        // Terminal state check: CLOSED accounts cannot transition to any other state
        if (currentStatus == AccountStatus.CLOSED) {
            auditService.logAction(actor, "ACCOUNT_STATUS_CHANGE_REJECTED", "ACCOUNT", id.toString(), "FAILURE", ip,
                    "Attempted transition from terminal state CLOSED");
            throw new InvalidAccountStateTransitionException("Cannot change status of an already closed account.");
        }

        // Enforce zero balance before closing
        if (targetStatus == AccountStatus.CLOSED) {
            if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
                auditService.logAction(actor, "ACCOUNT_CLOSE_REJECTED", "ACCOUNT", id.toString(), "FAILURE", ip,
                        "Non-zero balance: " + account.getBalance());
                throw new InvalidAccountStateTransitionException(
                        "Account cannot be closed with an outstanding non-zero balance: " + account.getBalance() + " " + account.getCurrency());
            }
        }

        account.setStatus(targetStatus);
        Account saved = accountRepository.save(account);

        // Evict cache
        evictBalanceCache(id);

        String metadata = String.format("{\"previousStatus\":\"%s\",\"newStatus\":\"%s\",\"reason\":\"%s\"}",
                currentStatus, targetStatus, req.reason() != null ? req.reason() : "N/A");

        auditService.logAction(actor, "ACCOUNT_STATUS_CHANGE", "ACCOUNT", id.toString(), "SUCCESS", ip, metadata);

        log.info("Account {} status changed: {} -> {}", id, currentStatus, targetStatus);
        return AccountResponse.fromEntity(saved);
    }

    public AccountBalanceResponse getAccountBalance(UUID id) {
        // Attempt Cache lookup
        if (cacheManager.isPresent()) {
            try {
                Cache cache = cacheManager.get().getCache(BALANCE_CACHE_NAME);
                if (cache != null) {
                    AccountBalanceResponse cached = cache.get(id, AccountBalanceResponse.class);
                    if (cached != null) {
                        return new AccountBalanceResponse(
                                cached.accountId(),
                                cached.accountNumber(),
                                cached.balance(),
                                cached.currency(),
                                cached.status(),
                                true,
                                cached.retrievedAt()
                        );
                    }
                }
            } catch (Exception ex) {
                log.warn("Redis/Cache lookup failed for account {}, falling back to PostgreSQL database: {}", id, ex.getMessage());
            }
        }

        // Database source of truth query
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + id));

        AccountBalanceResponse response = AccountBalanceResponse.fromEntity(account, false);

        // Populate Cache
        if (cacheManager.isPresent()) {
            try {
                Cache cache = cacheManager.get().getCache(BALANCE_CACHE_NAME);
                if (cache != null) {
                    cache.put(id, response);
                }
            } catch (Exception ex) {
                log.warn("Failed to populate cache for account {}: {}", id, ex.getMessage());
            }
        }

        return response;
    }

    public void evictBalanceCache(UUID accountId) {
        if (cacheManager.isPresent()) {
            try {
                Cache cache = cacheManager.get().getCache(BALANCE_CACHE_NAME);
                if (cache != null) {
                    cache.evict(accountId);
                }
            } catch (Exception ex) {
                log.warn("Cache eviction failed for account {}: {}", accountId, ex.getMessage());
            }
        }
    }

    public String generateUniqueAccountNumber() {
        for (int i = 0; i < 20; i++) {
            // Generates 12 digits e.g. "1234-5678-9012"
            int part1 = 1000 + secureRandom.nextInt(9000);
            int part2 = 1000 + secureRandom.nextInt(9000);
            int part3 = 1000 + secureRandom.nextInt(9000);
            String number = String.format("%04d-%04d-%04d", part1, part2, part3);
            if (!accountRepository.existsByAccountNumber(number)) {
                return number;
            }
        }
        // Fallback with timestamp
        return String.format("%04d-%04d-%04d",
                1000 + secureRandom.nextInt(9000),
                1000 + secureRandom.nextInt(9000),
                (int) (System.currentTimeMillis() % 10000));
    }
}
