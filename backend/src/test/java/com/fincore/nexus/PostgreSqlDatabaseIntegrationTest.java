package com.fincore.nexus;

import com.fincore.nexus.dto.request.CreateAccountRequest;
import com.fincore.nexus.dto.request.CreateCustomerRequest;
import com.fincore.nexus.dto.request.DepositRequest;
import com.fincore.nexus.dto.request.WithdrawRequest;
import com.fincore.nexus.dto.response.AccountResponse;
import com.fincore.nexus.dto.response.CustomerResponse;
import com.fincore.nexus.dto.response.TransactionResponse;
import com.fincore.nexus.entity.AccountType;
import com.fincore.nexus.service.AccountService;
import com.fincore.nexus.service.CustomerService;
import com.fincore.nexus.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5433/fincore_db",
        "spring.datasource.username=postgres",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "banking.cache.type=memory",
        "banking.kafka.enabled=false"
})
public class PostgreSqlDatabaseIntegrationTest {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionService transactionService;

    @Test
    @DisplayName("Verify real PostgreSQL 16 database connection, Flyway migrations, and atomic financial persistence")
    void testLivePostgreSqlEndToEnd() {
        String uniqueEmail = "pg.test." + System.currentTimeMillis() + "@fincore.bank";

        // 1. Create Customer in PostgreSQL
        CreateCustomerRequest custReq = new CreateCustomerRequest(
                "Alexander", "Hamilton", uniqueEmail, "+1-555-1789",
                "10 Exchange Pl", "Floor 12", "New York", "NY", "10005", "USA"
        );
        CustomerResponse customer = customerService.createCustomer(custReq, "admin", "127.0.0.1");
        assertThat(customer).isNotNull();
        assertThat(customer.id()).isNotNull();

        // 2. Create Account in PostgreSQL
        CreateAccountRequest accReq = new CreateAccountRequest(
                customer.id(), AccountType.SAVINGS, "USD", BigDecimal.valueOf(10447.50)
        );
        AccountResponse account = accountService.createAccount(accReq, "teller", "127.0.0.1");
        assertThat(account).isNotNull();
        assertThat(account.accountNumber()).matches("\\d{4}-\\d{4}-\\d{4}");
        assertThat(account.balance()).isEqualByComparingTo(BigDecimal.valueOf(10447.50));

        // 3. Atomic Deposit in PostgreSQL
        DepositRequest depReq = new DepositRequest(BigDecimal.valueOf(2400.00), "Paycheck Deposit", null);
        TransactionResponse depTxn = transactionService.deposit(account.id(), depReq, null, "teller", "127.0.0.1");
        assertThat(depTxn.resultingBalance()).isEqualByComparingTo(BigDecimal.valueOf(12847.50));

        // 4. Atomic Withdrawal in PostgreSQL
        WithdrawRequest wthReq = new WithdrawRequest(BigDecimal.valueOf(847.50), "ATM Withdrawal", null);
        TransactionResponse wthTxn = transactionService.withdraw(account.id(), wthReq, null, "customer", "127.0.0.1");
        assertThat(wthTxn.resultingBalance()).isEqualByComparingTo(BigDecimal.valueOf(12000.00));

        // 5. Verify final balance in PostgreSQL source of truth
        var balanceResp = accountService.getAccountBalance(account.id());
        assertThat(balanceResp.balance()).isEqualByComparingTo(BigDecimal.valueOf(12000.00));
        assertThat(balanceResp.currency()).isEqualTo("USD");
    }
}
