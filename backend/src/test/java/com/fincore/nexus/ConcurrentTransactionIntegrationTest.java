package com.fincore.nexus;

import com.fincore.nexus.dto.request.DepositRequest;
import com.fincore.nexus.dto.request.WithdrawRequest;
import com.fincore.nexus.entity.Account;
import com.fincore.nexus.entity.AccountType;
import com.fincore.nexus.entity.Customer;
import com.fincore.nexus.exception.InsufficientFundsException;
import com.fincore.nexus.repository.AccountRepository;
import com.fincore.nexus.repository.CustomerRepository;
import com.fincore.nexus.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class ConcurrentTransactionIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    private UUID accountId;

    @BeforeEach
    void setUp() {
        Customer customer = new Customer(UUID.randomUUID(), "Concur", "Tester",
                "concur." + System.currentTimeMillis() + "@test.bank", "+1-555-9000",
                null, null, null, null, null, "USA");
        customerRepository.save(customer);

        Account account = new Account(UUID.randomUUID(), "CONCUR-" + System.currentTimeMillis(),
                customer, AccountType.CHECKING, "USD", BigDecimal.valueOf(1000.00));
        Account saved = accountRepository.save(account);
        this.accountId = saved.getId();
    }

    @Test
    @DisplayName("Should prevent race conditions and overdraft under simultaneous concurrent withdrawals")
    void testConcurrentWithdrawalsSafety() throws InterruptedException {
        // Initial balance = $1,000.00.
        // 10 concurrent threads each attempt to withdraw $150.00 (Total attempted = $1,500.00).
        // Under pessimistic locking:
        // Exactly 6 withdrawals will succeed (6 * $150 = $900 withdrawn).
        // Exactly 4 withdrawals will fail with InsufficientFundsException.
        // Final balance must be exactly $100.00!
        int numThreads = 10;
        BigDecimal withdrawAmount = BigDecimal.valueOf(150.00);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);

        AtomicInteger successfulWithdrawals = new AtomicInteger(0);
        AtomicInteger rejectedWithdrawals = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Guarantee all threads start at the exact same moment
                    transactionService.withdraw(accountId,
                            new WithdrawRequest(withdrawAmount, "Concurrent ATM Test", null),
                            null, "concur-user", "127.0.0.1");
                    successfulWithdrawals.incrementAndGet();
                } catch (InsufficientFundsException ex) {
                    rejectedWithdrawals.incrementAndGet();
                } catch (Exception ex) {
                    // unexpected error
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        Account finalAccount = accountRepository.findById(accountId).orElseThrow();

        assertThat(successfulWithdrawals.get()).isEqualTo(6);
        assertThat(rejectedWithdrawals.get()).isEqualTo(4);
        assertThat(finalAccount.getBalance().setScale(2, RoundingMode.HALF_EVEN))
                .isEqualByComparingTo(BigDecimal.valueOf(100.00));
    }

    @Test
    @DisplayName("Should prevent lost updates during simultaneous concurrent deposits")
    void testConcurrentDepositsSafety() throws InterruptedException {
        // Initial balance = $1,000.00.
        // 10 concurrent threads each deposit $100.00 (Total deposited = $1,000.00).
        // Final balance must be exactly $2,000.00 without lost updates.
        int numThreads = 10;
        BigDecimal depositAmount = BigDecimal.valueOf(100.00);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);

        AtomicInteger successfulDeposits = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    transactionService.deposit(accountId,
                            new DepositRequest(depositAmount, "Concurrent Deposit Test", null),
                            null, "concur-user", "127.0.0.1");
                    successfulDeposits.incrementAndGet();
                } catch (Exception ex) {
                    // unexpected
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        Account finalAccount = accountRepository.findById(accountId).orElseThrow();

        assertThat(successfulDeposits.get()).isEqualTo(10);
        assertThat(finalAccount.getBalance().setScale(2, RoundingMode.HALF_EVEN))
                .isEqualByComparingTo(BigDecimal.valueOf(2000.00));
    }
}
