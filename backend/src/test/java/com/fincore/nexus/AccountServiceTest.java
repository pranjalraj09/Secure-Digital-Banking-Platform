package com.fincore.nexus;

import com.fincore.nexus.dto.request.CreateAccountRequest;
import com.fincore.nexus.dto.request.UpdateAccountStatusRequest;
import com.fincore.nexus.dto.response.AccountBalanceResponse;
import com.fincore.nexus.dto.response.AccountResponse;
import com.fincore.nexus.entity.Account;
import com.fincore.nexus.entity.AccountStatus;
import com.fincore.nexus.entity.AccountType;
import com.fincore.nexus.entity.Customer;
import com.fincore.nexus.exception.InvalidAccountStateTransitionException;
import com.fincore.nexus.exception.ResourceNotFoundException;
import com.fincore.nexus.repository.AccountRepository;
import com.fincore.nexus.repository.CustomerRepository;
import com.fincore.nexus.repository.TransactionRepository;
import com.fincore.nexus.service.AccountService;
import com.fincore.nexus.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AuditService auditService;

    private ConcurrentMapCacheManager cacheManager;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        cacheManager = new ConcurrentMapCacheManager(AccountService.BALANCE_CACHE_NAME);
        accountService = new AccountService(accountRepository, customerRepository, transactionRepository,
                auditService, Optional.of(cacheManager));
    }

    @Test
    @DisplayName("Should create account with unique account number and ACTIVE status")
    void testCreateAccountSuccess() {
        UUID customerId = UUID.randomUUID();
        Customer customer = new Customer(customerId, "Alice", "Wonder", "alice@wonder.com", "+1-555-4321",
                null, null, null, null, null, "USA");

        CreateAccountRequest req = new CreateAccountRequest(customerId, AccountType.SAVINGS, "USD", BigDecimal.valueOf(100.00));

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(accountRepository.existsByAccountNumber(any())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountResponse response = accountService.createAccount(req, "teller@fincore.bank", "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.accountNumber()).matches("\\d{4}-\\d{4}-\\d{4}");
        assertThat(response.accountType()).isEqualTo(AccountType.SAVINGS);
        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.balance()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
        assertThat(response.currency()).isEqualTo("USD");

        verify(transactionRepository).save(any());
        verify(auditService).logAction(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should successfully transition account status: ACTIVE -> FROZEN")
    void testAccountStatusTransitionActiveToFrozen() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account(accountId, "1234-5678-9012", new Customer(), AccountType.CHECKING, "USD", BigDecimal.valueOf(500));
        account.setStatus(AccountStatus.ACTIVE);

        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateAccountStatusRequest req = new UpdateAccountStatusRequest(AccountStatus.FROZEN, "Suspicious activity detected");
        AccountResponse response = accountService.updateAccountStatus(accountId, req, "admin@fincore.bank", "127.0.0.1");

        assertThat(response.status()).isEqualTo(AccountStatus.FROZEN);
    }

    @Test
    @DisplayName("Should reject closing an account with non-zero balance")
    void testAccountCloseRejectedWithNonZeroBalance() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account(accountId, "1234-5678-9012", new Customer(), AccountType.CHECKING, "USD", BigDecimal.valueOf(250.75));
        account.setStatus(AccountStatus.ACTIVE);

        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(account));

        UpdateAccountStatusRequest req = new UpdateAccountStatusRequest(AccountStatus.CLOSED, "Customer relocation");

        assertThatThrownBy(() -> accountService.updateAccountStatus(accountId, req, "admin@fincore.bank", "127.0.0.1"))
                .isInstanceOf(InvalidAccountStateTransitionException.class)
                .hasMessageContaining("non-zero balance");
    }

    @Test
    @DisplayName("Should allow closing an account when balance is strictly zero")
    void testAccountCloseAllowedWhenZeroBalance() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account(accountId, "1234-5678-9012", new Customer(), AccountType.CHECKING, "USD", BigDecimal.ZERO);
        account.setStatus(AccountStatus.ACTIVE);

        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateAccountStatusRequest req = new UpdateAccountStatusRequest(AccountStatus.CLOSED, "Account closed by user request");
        AccountResponse response = accountService.updateAccountStatus(accountId, req, "admin@fincore.bank", "127.0.0.1");

        assertThat(response.status()).isEqualTo(AccountStatus.CLOSED);
    }

    @Test
    @DisplayName("Should reject state transition from terminal state CLOSED")
    void testAccountStateTransitionFromClosedRejected() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account(accountId, "1234-5678-9012", new Customer(), AccountType.CHECKING, "USD", BigDecimal.ZERO);
        account.setStatus(AccountStatus.CLOSED);

        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(account));

        UpdateAccountStatusRequest req = new UpdateAccountStatusRequest(AccountStatus.ACTIVE, "Reactivation attempt");

        assertThatThrownBy(() -> accountService.updateAccountStatus(accountId, req, "admin@fincore.bank", "127.0.0.1"))
                .isInstanceOf(InvalidAccountStateTransitionException.class)
                .hasMessageContaining("already closed");
    }

    @Test
    @DisplayName("Should cache balance query and return cached=true on subsequent calls")
    void testBalanceCaching() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account(accountId, "1234-5678-9012", new Customer(), AccountType.SAVINGS, "USD", BigDecimal.valueOf(12847.50));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        // First call: DB query (cached = false)
        AccountBalanceResponse res1 = accountService.getAccountBalance(accountId);
        assertThat(res1.cached()).isFalse();
        assertThat(res1.balance()).isEqualByComparingTo(BigDecimal.valueOf(12847.50));

        // Second call: Cache hit (cached = true)
        AccountBalanceResponse res2 = accountService.getAccountBalance(accountId);
        assertThat(res2.cached()).isTrue();
        assertThat(res2.balance()).isEqualByComparingTo(BigDecimal.valueOf(12847.50));
    }
}
