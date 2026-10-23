package com.fincore.nexus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincore.nexus.dto.request.DepositRequest;
import com.fincore.nexus.dto.request.WithdrawRequest;
import com.fincore.nexus.dto.response.TransactionResponse;
import com.fincore.nexus.entity.Account;
import com.fincore.nexus.entity.AccountStatus;
import com.fincore.nexus.entity.AccountType;
import com.fincore.nexus.entity.Customer;
import com.fincore.nexus.entity.IdempotencyRecord;
import com.fincore.nexus.entity.Transaction;
import com.fincore.nexus.entity.TransactionType;
import com.fincore.nexus.event.TransactionCompletedEvent;
import com.fincore.nexus.exception.AccountClosedException;
import com.fincore.nexus.exception.AccountFrozenException;
import com.fincore.nexus.exception.InsufficientFundsException;
import com.fincore.nexus.exception.InvalidAmountException;
import com.fincore.nexus.repository.AccountRepository;
import com.fincore.nexus.repository.TransactionRepository;
import com.fincore.nexus.service.AccountService;
import com.fincore.nexus.service.AuditService;
import com.fincore.nexus.service.IdempotencyService;
import com.fincore.nexus.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private AuditService auditService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(accountRepository, transactionRepository,
                accountService, auditService, idempotencyService, eventPublisher, objectMapper);
    }

    @Test
    @DisplayName("Should successfully deposit funds, update balance, save transaction, and publish event")
    void testDepositSuccess() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account(accountId, "1234-5678-9012", new Customer(), AccountType.SAVINGS, "USD", BigDecimal.valueOf(10000.00));
        account.setStatus(AccountStatus.ACTIVE);

        DepositRequest req = new DepositRequest(BigDecimal.valueOf(2400.00), "Paycheck Deposit", "DEP-1001");

        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = transactionService.deposit(accountId, req, null, "teller@fincore.bank", "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.amount()).isEqualByComparingTo(BigDecimal.valueOf(2400.00));
        assertThat(response.previousBalance()).isEqualByComparingTo(BigDecimal.valueOf(10000.00));
        assertThat(response.resultingBalance()).isEqualByComparingTo(BigDecimal.valueOf(12400.00));
        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(12400.00));

        // Event published
        verify(eventPublisher).publishEvent(any(TransactionCompletedEvent.class));
        verify(accountService).evictBalanceCache(accountId);
    }

    @Test
    @DisplayName("Should successfully withdraw funds when sufficient balance is present")
    void testWithdrawSuccess() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account(accountId, "1234-5678-9012", new Customer(), AccountType.CHECKING, "USD", BigDecimal.valueOf(5000.00));
        account.setStatus(AccountStatus.ACTIVE);

        WithdrawRequest req = new WithdrawRequest(BigDecimal.valueOf(1500.00), "ATM Cash Withdrawal", "WTH-2002");

        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = transactionService.withdraw(accountId, req, null, "customer@fincore.bank", "127.0.0.1");

        assertThat(response.amount()).isEqualByComparingTo(BigDecimal.valueOf(1500.00));
        assertThat(response.previousBalance()).isEqualByComparingTo(BigDecimal.valueOf(5000.00));
        assertThat(response.resultingBalance()).isEqualByComparingTo(BigDecimal.valueOf(3500.00));
        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(3500.00));

        verify(eventPublisher).publishEvent(any(TransactionCompletedEvent.class));
    }

    @Test
    @DisplayName("Should reject withdrawal when insufficient funds are available")
    void testWithdrawInsufficientFunds() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account(accountId, "1234-5678-9012", new Customer(), AccountType.CHECKING, "USD", BigDecimal.valueOf(200.00));
        account.setStatus(AccountStatus.ACTIVE);

        WithdrawRequest req = new WithdrawRequest(BigDecimal.valueOf(500.00), "Rent Payment", "WTH-3003");

        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> transactionService.withdraw(accountId, req, null, "customer@fincore.bank", "127.0.0.1"))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("Insufficient funds");

        // Verify balance was not modified and transaction was not persisted
        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(200.00));
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Should reject deposit with zero or negative amount")
    void testDepositInvalidAmount() {
        UUID accountId = UUID.randomUUID();
        DepositRequest zeroReq = new DepositRequest(BigDecimal.ZERO, "Zero Deposit", null);
        DepositRequest negativeReq = new DepositRequest(BigDecimal.valueOf(-50.00), "Negative Deposit", null);

        assertThatThrownBy(() -> transactionService.deposit(accountId, zeroReq, null, "user", "ip"))
                .isInstanceOf(InvalidAmountException.class);

        assertThatThrownBy(() -> transactionService.deposit(accountId, negativeReq, null, "user", "ip"))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    @DisplayName("Should reject operations on FROZEN account")
    void testFrozenAccountOperationsRejected() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account(accountId, "1234-5678-9012", new Customer(), AccountType.CHECKING, "USD", BigDecimal.valueOf(1000.00));
        account.setStatus(AccountStatus.FROZEN);

        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(account));

        DepositRequest dep = new DepositRequest(BigDecimal.valueOf(100.00), "Deposit", null);
        WithdrawRequest wth = new WithdrawRequest(BigDecimal.valueOf(100.00), "Withdraw", null);

        assertThatThrownBy(() -> transactionService.deposit(accountId, dep, null, "user", "ip"))
                .isInstanceOf(AccountFrozenException.class);

        assertThatThrownBy(() -> transactionService.withdraw(accountId, wth, null, "user", "ip"))
                .isInstanceOf(AccountFrozenException.class);
    }

    @Test
    @DisplayName("Should reject operations on CLOSED account")
    void testClosedAccountOperationsRejected() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account(accountId, "1234-5678-9012", new Customer(), AccountType.CHECKING, "USD", BigDecimal.ZERO);
        account.setStatus(AccountStatus.CLOSED);

        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(account));

        DepositRequest dep = new DepositRequest(BigDecimal.valueOf(100.00), "Deposit", null);

        assertThatThrownBy(() -> transactionService.deposit(accountId, dep, null, "user", "ip"))
                .isInstanceOf(AccountClosedException.class);
    }

    @Test
    @DisplayName("Should return cached response for duplicate idempotency key without re-executing balance change")
    void testIdempotencyDeduplication() throws Exception {
        UUID accountId = UUID.randomUUID();
        DepositRequest req = new DepositRequest(BigDecimal.valueOf(250.00), "Deposit", "REF-IDEMP");
        String idempotencyKey = "idemp-key-xyz-123";

        TransactionResponse cachedResponse = new TransactionResponse(
                UUID.randomUUID(), accountId, "1234-5678-9012", TransactionType.DEPOSIT,
                BigDecimal.valueOf(250.00), BigDecimal.valueOf(1000.00), BigDecimal.valueOf(1250.00),
                com.fincore.nexus.entity.TransactionStatus.COMPLETED, "Deposit", "REF-IDEMP", null, java.time.Instant.now()
        );

        IdempotencyRecord record = new IdempotencyRecord(idempotencyKey, "dummyHash", 200, objectMapper.writeValueAsString(cachedResponse));

        when(idempotencyService.getRecord(idempotencyKey)).thenReturn(Optional.of(record));

        TransactionResponse response = transactionService.deposit(accountId, req, idempotencyKey, "user", "ip");

        assertThat(response).isNotNull();
        assertThat(response.resultingBalance()).isEqualByComparingTo(BigDecimal.valueOf(1250.00));

        // DB lock, save, and event publish must NOT be called on deduplicated execution
        verify(accountRepository, never()).findByIdWithLock(any());
        verify(transactionRepository, never()).save(any());
    }
}
