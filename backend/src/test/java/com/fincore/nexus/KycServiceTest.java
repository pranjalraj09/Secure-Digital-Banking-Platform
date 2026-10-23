package com.fincore.nexus;

import com.fincore.nexus.dto.request.KycUpdateRequest;
import com.fincore.nexus.dto.response.KycResponse;
import com.fincore.nexus.entity.Customer;
import com.fincore.nexus.entity.KycStatus;
import com.fincore.nexus.entity.RiskLevel;
import com.fincore.nexus.exception.ResourceNotFoundException;
import com.fincore.nexus.repository.CustomerRepository;
import com.fincore.nexus.service.AuditService;
import com.fincore.nexus.service.KycService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KycServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private AuditService auditService;

    private KycService kycService;

    @BeforeEach
    void setUp() {
        kycService = new KycService(customerRepository, auditService);
    }

    @Test
    @DisplayName("Should successfully update customer KYC status and record audit log")
    void testUpdateKycStatusSuccess() {
        UUID customerId = UUID.randomUUID();
        Customer customer = new Customer(customerId, "Jane", "Doe", "jane@fincore.bank", "+1-555-8888",
                null, null, null, null, null, null);
        customer.setKycStatus(KycStatus.PENDING);

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        KycUpdateRequest req = new KycUpdateRequest(KycStatus.VERIFIED, "Aadhar and PAN verified", RiskLevel.LOW);
        KycResponse response = kycService.updateKycStatus(customerId, req, "teller@fincore.bank", "127.0.0.1");

        assertThat(response.kycStatus()).isEqualTo(KycStatus.VERIFIED);
        assertThat(response.kycNotes()).isEqualTo("Aadhar and PAN verified");
        assertThat(response.riskLevel()).isEqualTo(RiskLevel.LOW);

        verify(auditService).logAction(eq("teller@fincore.bank"), eq("KYC_STATUS_UPDATE"), eq("CUSTOMER"),
                eq(customerId.toString()), eq("SUCCESS"), eq("127.0.0.1"), contains("VERIFIED"));
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when customer not found during KYC update")
    void testUpdateKycCustomerNotFound() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

        KycUpdateRequest req = new KycUpdateRequest(KycStatus.VERIFIED, "Verified", RiskLevel.LOW);

        assertThatThrownBy(() -> kycService.updateKycStatus(customerId, req, "teller", "ip"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
