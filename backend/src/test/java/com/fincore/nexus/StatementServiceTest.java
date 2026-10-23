package com.fincore.nexus;

import com.fincore.nexus.dto.response.AccountStatementResponse;
import com.fincore.nexus.entity.Account;
import com.fincore.nexus.entity.AccountType;
import com.fincore.nexus.entity.Customer;
import com.fincore.nexus.entity.Transaction;
import com.fincore.nexus.entity.TransactionStatus;
import com.fincore.nexus.entity.TransactionType;
import com.fincore.nexus.repository.AccountRepository;
import com.fincore.nexus.repository.TransactionRepository;
import com.fincore.nexus.service.StatementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatementServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    private StatementService statementService;

    @BeforeEach
    void setUp() {
        statementService = new StatementService(accountRepository, transactionRepository);
    }

    @Test
    @DisplayName("Should generate statement with correct deposit, withdrawal totals and balances")
    void testGenerateStatementSuccess() {
        UUID accountId = UUID.randomUUID();
        Customer customer = new Customer(UUID.randomUUID(), "John", "Smith", "john@fincore.bank", "+1-555-1234",
                null, null, null, null, null, null);
        Account account = new Account(accountId, "1234-5678-9012", customer, AccountType.SAVINGS, "USD", BigDecimal.valueOf(12847.50));

        Instant now = Instant.now();
        Instant start = now.minus(10, ChronoUnit.DAYS);
        Instant end = now;

        // Transactions ordered by timestamp DESC (latest first)
        Transaction t2 = new Transaction(UUID.randomUUID(), account, TransactionType.DEPOSIT,
                BigDecimal.valueOf(2400.00), BigDecimal.valueOf(10447.50), BigDecimal.valueOf(12847.50),
                TransactionStatus.COMPLETED, "Deposit", "REF-2");
        t2.setTimestamp(now.minus(2, ChronoUnit.DAYS));

        Transaction t1 = new Transaction(UUID.randomUUID(), account, TransactionType.WITHDRAWAL,
                BigDecimal.valueOf(500.00), BigDecimal.valueOf(10947.50), BigDecimal.valueOf(10447.50),
                TransactionStatus.COMPLETED, "Withdrawal", "REF-1");
        t1.setTimestamp(now.minus(5, ChronoUnit.DAYS));

        List<Transaction> txns = List.of(t2, t1);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(transactionRepository.findByAccountIdAndTimestampBetweenOrderByTimestampDesc(eq(accountId), any(), any(), any()))
                .thenReturn(new PageImpl<>(txns, PageRequest.of(0, 50), 2));

        AccountStatementResponse statement = statementService.generateStatement(accountId, start, end, PageRequest.of(0, 50));

        assertThat(statement).isNotNull();
        assertThat(statement.accountNumber()).isEqualTo("1234-5678-9012");
        assertThat(statement.customerName()).isEqualTo("John Smith");
        assertThat(statement.totalDeposits()).isEqualByComparingTo(BigDecimal.valueOf(2400.00));
        assertThat(statement.totalWithdrawals()).isEqualByComparingTo(BigDecimal.valueOf(500.00));
        assertThat(statement.openingBalance()).isEqualByComparingTo(BigDecimal.valueOf(10947.50));
        assertThat(statement.closingBalance()).isEqualByComparingTo(BigDecimal.valueOf(12847.50));
        assertThat(statement.transactionCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should reject statement generation when startDate is after endDate")
    void testInvalidDateRange() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account(accountId, "1234-5678-9012", new Customer(), AccountType.SAVINGS, "USD", BigDecimal.valueOf(1000));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        Instant now = Instant.now();
        Instant future = now.plus(5, ChronoUnit.DAYS);

        assertThatThrownBy(() -> statementService.generateStatement(accountId, future, now, PageRequest.of(0, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("startDate cannot be after endDate");
    }
}
