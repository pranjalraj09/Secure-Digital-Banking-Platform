package com.fincore.nexus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincore.nexus.dto.request.CreateCustomerRequest;
import com.fincore.nexus.dto.request.KycUpdateRequest;
import com.fincore.nexus.entity.Account;
import com.fincore.nexus.entity.AccountType;
import com.fincore.nexus.entity.Customer;
import com.fincore.nexus.entity.KycStatus;
import com.fincore.nexus.entity.RiskLevel;
import com.fincore.nexus.repository.AccountRepository;
import com.fincore.nexus.repository.CustomerRepository;
import com.fincore.nexus.security.DevJwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SecurityRbacIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DevJwtService devJwtService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Customer customer1;
    private Customer customer2;
    private Account account1;
    private String adminToken;
    private String tellerToken;
    private String customer1Token;
    private String customer2Token;

    @BeforeEach
    void setUp() {
        customerRepository.deleteAll();
        accountRepository.deleteAll();

        customer1 = new Customer(UUID.randomUUID(), "Alice", "Customer", "alice@customer.bank", "+1-555-1001",
                null, null, null, null, null, "USA");
        customerRepository.save(customer1);

        customer2 = new Customer(UUID.randomUUID(), "Bob", "Customer", "bob@customer.bank", "+1-555-1002",
                null, null, null, null, null, "USA");
        customerRepository.save(customer2);

        account1 = new Account(UUID.randomUUID(), "1111-2222-3333", customer1, AccountType.CHECKING, "USD", BigDecimal.valueOf(500.00));
        accountRepository.save(account1);

        adminToken = "Bearer " + devJwtService.generateToken("admin@fincore.bank", "ADMIN", null);
        tellerToken = "Bearer " + devJwtService.generateToken("teller@fincore.bank", "TELLER", null);
        customer1Token = "Bearer " + devJwtService.generateToken("alice@customer.bank", "CUSTOMER", customer1.getId());
        customer2Token = "Bearer " + devJwtService.generateToken("bob@customer.bank", "CUSTOMER", customer2.getId());
    }

    @Test
    @DisplayName("Admin can view audit logs")
    void testAdminCanViewAuditLogs() throws Exception {
        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Customer cannot view audit logs (Forbidden 403)")
    void testCustomerCannotViewAuditLogs() throws Exception {
        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", customer1Token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Customer can access their own account details")
    void testCustomerAccessOwnAccount() throws Exception {
        mockMvc.perform(get("/api/accounts/" + account1.getId())
                        .header("Authorization", customer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("1111-2222-3333"));
    }

    @Test
    @DisplayName("Customer cannot access another customer's account details (Forbidden 403)")
    void testCustomerCannotAccessOtherCustomerAccount() throws Exception {
        mockMvc.perform(get("/api/accounts/" + account1.getId())
                        .header("Authorization", customer2Token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Teller can update customer KYC status")
    void testTellerCanUpdateKyc() throws Exception {
        KycUpdateRequest req = new KycUpdateRequest(KycStatus.VERIFIED, "Documents verified at branch", RiskLevel.LOW);

        mockMvc.perform(patch("/api/customers/" + customer1.getId() + "/kyc")
                        .header("Authorization", tellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kycStatus").value("VERIFIED"));
    }

    @Test
    @DisplayName("Customer cannot update their own KYC status (Forbidden 403)")
    void testCustomerCannotUpdateKyc() throws Exception {
        KycUpdateRequest req = new KycUpdateRequest(KycStatus.VERIFIED, "Self approval", RiskLevel.LOW);

        mockMvc.perform(patch("/api/customers/" + customer1.getId() + "/kyc")
                        .header("Authorization", customer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }
}
