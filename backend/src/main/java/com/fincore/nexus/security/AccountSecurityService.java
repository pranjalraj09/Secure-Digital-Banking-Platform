package com.fincore.nexus.security;

import com.fincore.nexus.entity.Account;
import com.fincore.nexus.entity.Customer;
import com.fincore.nexus.repository.AccountRepository;
import com.fincore.nexus.repository.CustomerRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service("accountSecurityService")
public class AccountSecurityService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;

    public AccountSecurityService(AccountRepository accountRepository, CustomerRepository customerRepository) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public boolean isCustomerOwner(Authentication authentication, UUID customerId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        // Tellers and Admins have broad authorized access
        if (hasStaffRole(authentication)) {
            return true;
        }

        String username = authentication.getName();
        Optional<Customer> customerOpt = customerRepository.findById(customerId);
        if (customerOpt.isEmpty()) {
            return false;
        }

        Customer customer = customerOpt.get();
        return username.equalsIgnoreCase(customer.getEmail()) || username.equalsIgnoreCase(customer.getId().toString());
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public boolean isAccountOwner(Authentication authentication, UUID accountId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        // Tellers and Admins have broad authorized access
        if (hasStaffRole(authentication)) {
            return true;
        }

        String username = authentication.getName();
        Optional<Account> accountOpt = accountRepository.findById(accountId);
        if (accountOpt.isEmpty() || accountOpt.get().getCustomer() == null) {
            return false;
        }

        Customer customer = accountOpt.get().getCustomer();
        return username.equalsIgnoreCase(customer.getEmail()) || username.equalsIgnoreCase(customer.getId().toString());
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public boolean isAccountNumberOwner(Authentication authentication, String accountNumber) {
        if (authentication == null || !authentication.isAuthenticated() || accountNumber == null) {
            return false;
        }

        if (hasStaffRole(authentication)) {
            return true;
        }

        String username = authentication.getName();
        Optional<Account> accountOpt = accountRepository.findByAccountNumber(accountNumber.trim());
        if (accountOpt.isEmpty() || accountOpt.get().getCustomer() == null) {
            return false;
        }

        Customer customer = accountOpt.get().getCustomer();
        return username.equalsIgnoreCase(customer.getEmail()) || username.equalsIgnoreCase(customer.getId().toString());
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public boolean isTransferSourceAuthorized(Authentication authentication, com.fincore.nexus.dto.request.TransferRequest req) {
        if (req == null) {
            return false;
        }
        if (hasStaffRole(authentication)) {
            return true;
        }
        if (req.sourceAccountId() != null) {
            return isAccountOwner(authentication, req.sourceAccountId());
        }
        if (req.sourceAccountNumber() != null && !req.sourceAccountNumber().isBlank()) {
            return isAccountNumberOwner(authentication, req.sourceAccountNumber());
        }
        return false;
    }

    private boolean hasStaffRole(Authentication authentication) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String role = authority.getAuthority();
            if ("ROLE_ADMIN".equals(role) || "ROLE_TELLER".equals(role) || "ROLE_AUDITOR".equals(role)) {
                return true;
            }
        }
        return false;
    }
}
