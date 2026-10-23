package com.fincore.nexus.controller;

import com.fincore.nexus.dto.request.CreateCustomerRequest;
import com.fincore.nexus.dto.request.KycUpdateRequest;
import com.fincore.nexus.dto.request.UpdateCustomerRequest;
import com.fincore.nexus.dto.response.AccountResponse;
import com.fincore.nexus.dto.response.CustomerResponse;
import com.fincore.nexus.dto.response.KycResponse;
import com.fincore.nexus.service.CustomerService;
import com.fincore.nexus.service.KycService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
@Tag(name = "Customer Management & KYC", description = "Endpoints for customer registration, profile management, and KYC verification")
public class CustomerController {

    private final CustomerService customerService;
    private final KycService kycService;

    public CustomerController(CustomerService customerService, KycService kycService) {
        this.customerService = customerService;
        this.kycService = kycService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN', 'CUSTOMER')")
    @Operation(summary = "Register Customer", description = "Onboard a new customer record with initial PENDING KYC status")
    public ResponseEntity<CustomerResponse> createCustomer(
            @Valid @RequestBody CreateCustomerRequest req,
            Authentication auth,
            HttpServletRequest request) {
        String actor = auth != null ? auth.getName() : "SYSTEM";
        String ip = request.getRemoteAddr();
        CustomerResponse created = customerService.createCustomer(req, actor, ip);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('TELLER') or hasRole('ADMIN') or @accountSecurityService.isCustomerOwner(authentication, #id)")
    @Operation(summary = "Get Customer by ID", description = "Retrieve a customer profile by unique ID")
    public ResponseEntity<CustomerResponse> getCustomerById(@PathVariable UUID id) {
        return ResponseEntity.ok(customerService.getCustomerById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    @Operation(summary = "List Customers", description = "List registered customers with pagination")
    public ResponseEntity<Page<CustomerResponse>> listCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(customerService.listCustomers(pageable));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TELLER') or hasRole('ADMIN') or @accountSecurityService.isCustomerOwner(authentication, #id)")
    @Operation(summary = "Update Customer Profile", description = "Update permitted customer details")
    public ResponseEntity<CustomerResponse> updateCustomer(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCustomerRequest req,
            Authentication auth,
            HttpServletRequest request) {
        String actor = auth != null ? auth.getName() : "SYSTEM";
        String ip = request.getRemoteAddr();
        return ResponseEntity.ok(customerService.updateCustomer(id, req, actor, ip));
    }

    @GetMapping("/{id}/accounts")
    @PreAuthorize("hasRole('TELLER') or hasRole('ADMIN') or @accountSecurityService.isCustomerOwner(authentication, #id)")
    @Operation(summary = "Get Customer Accounts", description = "List all bank accounts belonging to a customer")
    public ResponseEntity<List<AccountResponse>> getCustomerAccounts(@PathVariable UUID id) {
        return ResponseEntity.ok(customerService.getCustomerAccounts(id));
    }

    @GetMapping("/{id}/kyc")
    @PreAuthorize("hasRole('TELLER') or hasRole('ADMIN') or @accountSecurityService.isCustomerOwner(authentication, #id)")
    @Operation(summary = "Get KYC Status", description = "View KYC verification state and risk assessment")
    public ResponseEntity<KycResponse> getKycStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(kycService.getKycStatus(id));
    }

    @PatchMapping("/{id}/kyc")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    @Operation(summary = "Update KYC Status", description = "Review and update KYC status (PENDING, VERIFIED, REJECTED). Restricted to Bank Staff.")
    public ResponseEntity<KycResponse> updateKycStatus(
            @PathVariable UUID id,
            @Valid @RequestBody KycUpdateRequest req,
            Authentication auth,
            HttpServletRequest request) {
        String actor = auth != null ? auth.getName() : "SYSTEM";
        String ip = request.getRemoteAddr();
        return ResponseEntity.ok(kycService.updateKycStatus(id, req, actor, ip));
    }
}
