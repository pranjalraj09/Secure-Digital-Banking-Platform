package com.fincore.nexus.service;

import com.fincore.nexus.dto.request.CreateCustomerRequest;
import com.fincore.nexus.dto.request.UpdateCustomerRequest;
import com.fincore.nexus.dto.response.AccountResponse;
import com.fincore.nexus.dto.response.CustomerResponse;
import com.fincore.nexus.entity.Customer;
import com.fincore.nexus.exception.DuplicateResourceException;
import com.fincore.nexus.exception.ResourceNotFoundException;
import com.fincore.nexus.repository.AccountRepository;
import com.fincore.nexus.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final AuditService auditService;

    public CustomerService(CustomerRepository customerRepository,
                           AccountRepository accountRepository,
                           AuditService auditService) {
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.auditService = auditService;
    }

    @Transactional
    public CustomerResponse createCustomer(CreateCustomerRequest req, String actor, String ip) {
        log.info("Creating customer with email: {}", req.email());

        if (customerRepository.existsByEmail(req.email())) {
            auditService.logAction(actor, "CUSTOMER_CREATE_FAILED", "CUSTOMER", req.email(), "FAILURE", ip, "Duplicate email");
            throw new DuplicateResourceException("Customer with email '" + req.email() + "' already exists.");
        }

        Customer customer = new Customer(
                UUID.randomUUID(),
                req.firstName().trim(),
                req.lastName().trim(),
                req.email().trim().toLowerCase(),
                req.phoneNumber().trim(),
                req.addressLine1(),
                req.addressLine2(),
                req.city(),
                req.state(),
                req.postalCode(),
                req.country()
        );

        Customer saved = customerRepository.save(customer);

        auditService.logAction(actor, "CUSTOMER_CREATE", "CUSTOMER", saved.getId().toString(), "SUCCESS", ip,
                "Created customer: " + saved.getFullName());

        return CustomerResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomerById(UUID id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + id));
        return CustomerResponse.fromEntity(customer);
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> listCustomers(Pageable pageable) {
        return customerRepository.findAll(pageable)
                .map(CustomerResponse::fromEntity);
    }

    @Transactional
    public CustomerResponse updateCustomer(UUID id, UpdateCustomerRequest req, String actor, String ip) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + id));

        if (req.firstName() != null && !req.firstName().isBlank()) {
            customer.setFirstName(req.firstName().trim());
        }
        if (req.lastName() != null && !req.lastName().isBlank()) {
            customer.setLastName(req.lastName().trim());
        }
        if (req.phoneNumber() != null && !req.phoneNumber().isBlank()) {
            customer.setPhoneNumber(req.phoneNumber().trim());
        }
        if (req.addressLine1() != null) customer.setAddressLine1(req.addressLine1());
        if (req.addressLine2() != null) customer.setAddressLine2(req.addressLine2());
        if (req.city() != null) customer.setCity(req.city());
        if (req.state() != null) customer.setState(req.state());
        if (req.postalCode() != null) customer.setPostalCode(req.postalCode());
        if (req.country() != null) customer.setCountry(req.country());

        Customer updated = customerRepository.save(customer);

        auditService.logAction(actor, "CUSTOMER_UPDATE", "CUSTOMER", updated.getId().toString(), "SUCCESS", ip,
                "Updated customer profile for: " + updated.getFullName());

        return CustomerResponse.fromEntity(updated);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getCustomerAccounts(UUID customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer not found with ID: " + customerId);
        }
        return accountRepository.findByCustomerId(customerId)
                .stream()
                .map(AccountResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public Customer getCustomerEntity(UUID customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));
    }
}
