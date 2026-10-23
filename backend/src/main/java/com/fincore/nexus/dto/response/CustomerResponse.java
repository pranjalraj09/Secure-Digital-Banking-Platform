package com.fincore.nexus.dto.response;

import com.fincore.nexus.entity.Customer;
import com.fincore.nexus.entity.KycStatus;
import com.fincore.nexus.entity.RiskLevel;

import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String firstName,
        String lastName,
        String fullName,
        String email,
        String phoneNumber,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,
        String country,
        KycStatus kycStatus,
        String kycNotes,
        RiskLevel riskLevel,
        Instant createdAt,
        Instant updatedAt
) {
    public static CustomerResponse fromEntity(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getFullName(),
                customer.getEmail(),
                customer.getPhoneNumber(),
                customer.getAddressLine1(),
                customer.getAddressLine2(),
                customer.getCity(),
                customer.getState(),
                customer.getPostalCode(),
                customer.getCountry(),
                customer.getKycStatus(),
                customer.getKycNotes(),
                customer.getRiskLevel(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }
}
