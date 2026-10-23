package com.fincore.nexus.controller;

import com.fincore.nexus.dto.request.CreateAccountRequest;
import com.fincore.nexus.dto.request.DepositRequest;
import com.fincore.nexus.dto.request.UpdateAccountStatusRequest;
import com.fincore.nexus.dto.request.WithdrawRequest;
import com.fincore.nexus.dto.response.AccountBalanceResponse;
import com.fincore.nexus.dto.response.AccountResponse;
import com.fincore.nexus.dto.response.AccountStatementResponse;
import com.fincore.nexus.dto.response.TransactionResponse;
import com.fincore.nexus.service.AccountService;
import com.fincore.nexus.service.StatementService;
import com.fincore.nexus.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Account & Transaction Services", description = "Core banking operations: account creation, lifecycle, deposits, withdrawals, and statement generation")
public class AccountController {

    private final AccountService accountService;
    private final TransactionService transactionService;
    private final StatementService statementService;

    public AccountController(AccountService accountService,
                             TransactionService transactionService,
                             StatementService statementService) {
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.statementService = statementService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    @Operation(summary = "Create Account", description = "Open a new bank account with server-generated unique account number")
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest req,
            Authentication auth,
            HttpServletRequest request) {
        String actor = auth != null ? auth.getName() : "SYSTEM";
        String ip = request.getRemoteAddr();
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccount(req, actor, ip));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('TELLER') or hasRole('ADMIN') or @accountSecurityService.isAccountOwner(authentication, #id)")
    @Operation(summary = "Get Account by ID", description = "Retrieve account details by unique ID")
    public ResponseEntity<AccountResponse> getAccountById(@PathVariable UUID id) {
        return ResponseEntity.ok(accountService.getAccountById(id));
    }

    @GetMapping("/number/{accountNumber}")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    @Operation(summary = "Get Account by Number", description = "Retrieve account details by account number")
    public ResponseEntity<AccountResponse> getAccountByNumber(@PathVariable String accountNumber) {
        return ResponseEntity.ok(accountService.getAccountByNumber(accountNumber));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    @Operation(summary = "List Accounts", description = "List all accounts with pagination")
    public ResponseEntity<Page<AccountResponse>> listAccounts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(accountService.listAccounts(pageable));
    }

    @GetMapping("/{id}/balance")
    @PreAuthorize("hasRole('TELLER') or hasRole('ADMIN') or @accountSecurityService.isAccountOwner(authentication, #id)")
    @Operation(summary = "Get Account Balance", description = "Retrieve real-time account balance with Redis cache optimization")
    public ResponseEntity<AccountBalanceResponse> getAccountBalance(@PathVariable UUID id) {
        return ResponseEntity.ok(accountService.getAccountBalance(id));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    @Operation(summary = "Update Account Lifecycle Status", description = "Change account status (ACTIVE, FROZEN, CLOSED) with validation rules")
    public ResponseEntity<AccountResponse> updateAccountStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAccountStatusRequest req,
            Authentication auth,
            HttpServletRequest request) {
        String actor = auth != null ? auth.getName() : "SYSTEM";
        String ip = request.getRemoteAddr();
        return ResponseEntity.ok(accountService.updateAccountStatus(id, req, actor, ip));
    }

    @PostMapping("/{id}/transactions/deposit")
    @PreAuthorize("hasRole('TELLER') or hasRole('ADMIN') or @accountSecurityService.isAccountOwner(authentication, #id)")
    @Operation(summary = "Deposit Funds", description = "Execute atomic deposit with pessimistic balance lock and Kafka event dispatch")
    public ResponseEntity<TransactionResponse> deposit(
            @PathVariable UUID id,
            @Valid @RequestBody DepositRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication auth,
            HttpServletRequest request) {
        String actor = auth != null ? auth.getName() : "SYSTEM";
        String ip = request.getRemoteAddr();
        return ResponseEntity.ok(transactionService.deposit(id, req, idempotencyKey, actor, ip));
    }

    @PostMapping("/{id}/transactions/withdraw")
    @PreAuthorize("hasRole('TELLER') or hasRole('ADMIN') or @accountSecurityService.isAccountOwner(authentication, #id)")
    @Operation(summary = "Withdraw Funds", description = "Execute atomic withdrawal with pessimistic balance lock, overdraft check, and Kafka event dispatch")
    public ResponseEntity<TransactionResponse> withdraw(
            @PathVariable UUID id,
            @Valid @RequestBody WithdrawRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication auth,
            HttpServletRequest request) {
        String actor = auth != null ? auth.getName() : "SYSTEM";
        String ip = request.getRemoteAddr();
        return ResponseEntity.ok(transactionService.withdraw(id, req, idempotencyKey, actor, ip));
    }

    @GetMapping("/{id}/transactions")
    @PreAuthorize("hasRole('TELLER') or hasRole('ADMIN') or @accountSecurityService.isAccountOwner(authentication, #id)")
    @Operation(summary = "List Account Transactions", description = "List historical transactions for an account with pagination")
    public ResponseEntity<Page<TransactionResponse>> getTransactions(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(transactionService.getTransactions(id, pageable));
    }

    @GetMapping("/{id}/statement")
    @PreAuthorize("hasRole('TELLER') or hasRole('ADMIN') or @accountSecurityService.isAccountOwner(authentication, #id)")
    @Operation(summary = "Generate Account Statement", description = "Generate official bank statement between specified date boundaries")
    public ResponseEntity<AccountStatementResponse> getStatement(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(statementService.generateStatement(id, startDate, endDate, pageable));
    }
}
