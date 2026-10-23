package com.fincore.nexus;

import com.fincore.nexus.dto.request.CreateCustomerRequest;
import com.fincore.nexus.dto.request.UpdateCustomerRequest;
import com.fincore.nexus.dto.response.CustomerResponse;
import com.fincore.nexus.entity.Customer;
import com.fincore.nexus.entity.KycStatus;
import com.fincore.nexus.entity.RiskLevel;
import com.fincore.nexus.exception.DuplicateResourceException;
import com.fincore.nexus.exception.ResourceNotFoundException;
import com.fincore.nexus.repository.AccountRepository;
import com.fincore.nexus.repository.CustomerRepository;
import com.fincore.nexus.service.AuditService;
import com.fincore.nexus.service.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AuditService auditService;

    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(customerRepository, accountRepository, auditService);
    }

    @Test
    @DisplayName("Should create customer successfully with default PENDING KYC status")
    void testCreateCustomerSuccess() {
        CreateCustomerRequest request = new CreateCustomerRequest(
                "John", "Smith", "john.smith@example.com", "+1-555-0199",
                "123 Wall St", "Suite 400", "New York", "NY", "10005", "USA"
        );

        when(customerRepository.existsByEmail("john.smith@example.com")).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer c = invocation.getArgument(0);
            c.setKycStatus(KycStatus.PENDING);
            c.setRiskLevel(RiskLevel.LOW);
            return c;
        });

        CustomerResponse response = customerService.createCustomer(request, "teller@fincore.bank", "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.firstName()).isEqualTo("John");
        assertThat(response.lastName()).isEqualTo("Smith");
        assertThat(response.fullName()).isEqualTo("John Smith");
        assertThat(response.email()).isEqualTo("john.smith@example.com");
        assertThat(response.kycStatus()).isEqualTo(KycStatus.PENDING);
        assertThat(response.riskLevel()).isEqualTo(RiskLevel.LOW);

        verify(auditService).logAction(eq("teller@fincore.bank"), eq("CUSTOMER_CREATE"), eq("CUSTOMER"),
                any(), eq("SUCCESS"), eq("127.0.0.1"), any());
    }

    @Test
    @DisplayName("Should reject customer creation when email already exists")
    void testCreateCustomerDuplicateEmail() {
        CreateCustomerRequest request = new CreateCustomerRequest(
                "John", "Smith", "duplicate@example.com", "+1-555-0199",
                null, null, null, null, null, null
        );

        when(customerRepository.existsByEmail("duplicate@example.com")).thenReturn(true);

        assertThatThrownBy(() -> customerService.createCustomer(request, "teller@fincore.bank", "127.0.0.1"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Should retrieve customer by ID")
    void testGetCustomerByIdSuccess() {
        UUID id = UUID.randomUUID();
        Customer customer = new Customer(id, "Jane", "Doe", "jane@example.com", "+1-555-0122",
                null, null, null, null, null, "USA");

        when(customerRepository.findById(id)).thenReturn(Optional.of(customer));

        CustomerResponse response = customerService.getCustomerById(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.email()).isEqualTo("jane@example.com");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when customer ID not found")
    void testGetCustomerByIdNotFound() {
        UUID id = UUID.randomUUID();
        when(customerRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getCustomerById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should update permitted customer fields")
    void testUpdateCustomerSuccess() {
        UUID id = UUID.randomUUID();
        Customer customer = new Customer(id, "Bob", "Miller", "bob@example.com", "+1-555-1111",
                "Old St", null, "Austin", "TX", "73301", "USA");

        when(customerRepository.findById(id)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateCustomerRequest updateReq = new UpdateCustomerRequest(
                "Robert", "Miller", "+1-555-9999", "New St", null, "Dallas", "TX", "75001", "USA"
        );

        CustomerResponse response = customerService.updateCustomer(id, updateReq, "admin@fincore.bank", "127.0.0.1");

        assertThat(response.firstName()).isEqualTo("Robert");
        assertThat(response.phoneNumber()).isEqualTo("+1-555-9999");
        assertThat(response.city()).isEqualTo("Dallas");
    }

    @Test
    @DisplayName("Should paginate customer listing")
    void testListCustomers() {
        Customer c1 = new Customer(UUID.randomUUID(), "Alice", "A", "alice@example.com", "+1-555-0001", null, null, null, null, null, null);
        Customer c2 = new Customer(UUID.randomUUID(), "Bob", "B", "bob@example.com", "+1-555-0002", null, null, null, null, null, null);

        PageRequest pageRequest = PageRequest.of(0, 10);
        when(customerRepository.findAll(pageRequest)).thenReturn(new PageImpl<>(List.of(c1, c2), pageRequest, 2));

        Page<CustomerResponse> page = customerService.listCustomers(pageRequest);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(2);
    }
}
