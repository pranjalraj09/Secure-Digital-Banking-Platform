package com.fincore.nexus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "previous_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal previousBalance;

    @Column(name = "resulting_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal resultingBalance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(length = 255)
    private String description;

    @Column(name = "reference_id", unique = true, length = 100)
    private String referenceId;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    public Transaction() {
    }

    public Transaction(UUID id, Account account, TransactionType transactionType, BigDecimal amount,
                       BigDecimal previousBalance, BigDecimal resultingBalance, TransactionStatus status,
                       String description, String referenceId) {
        this.id = id != null ? id : UUID.randomUUID();
        this.account = account;
        this.transactionType = transactionType;
        this.amount = amount != null ? amount.setScale(4, RoundingMode.HALF_EVEN) : BigDecimal.ZERO;
        this.previousBalance = previousBalance != null ? previousBalance.setScale(4, RoundingMode.HALF_EVEN) : BigDecimal.ZERO;
        this.resultingBalance = resultingBalance != null ? resultingBalance.setScale(4, RoundingMode.HALF_EVEN) : BigDecimal.ZERO;
        this.status = status != null ? status : TransactionStatus.COMPLETED;
        this.description = description;
        this.referenceId = referenceId != null ? referenceId : "TXN-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.timestamp == null) {
            this.timestamp = Instant.now();
        }
        if (this.amount != null) {
            this.amount = this.amount.setScale(4, RoundingMode.HALF_EVEN);
        }
        if (this.previousBalance != null) {
            this.previousBalance = this.previousBalance.setScale(4, RoundingMode.HALF_EVEN);
        }
        if (this.resultingBalance != null) {
            this.resultingBalance = this.resultingBalance.setScale(4, RoundingMode.HALF_EVEN);
        }
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount != null ? amount.setScale(4, RoundingMode.HALF_EVEN) : BigDecimal.ZERO;
    }

    public BigDecimal getPreviousBalance() {
        return previousBalance;
    }

    public void setPreviousBalance(BigDecimal previousBalance) {
        this.previousBalance = previousBalance != null ? previousBalance.setScale(4, RoundingMode.HALF_EVEN) : BigDecimal.ZERO;
    }

    public BigDecimal getResultingBalance() {
        return resultingBalance;
    }

    public void setResultingBalance(BigDecimal resultingBalance) {
        this.resultingBalance = resultingBalance != null ? resultingBalance.setScale(4, RoundingMode.HALF_EVEN) : BigDecimal.ZERO;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
