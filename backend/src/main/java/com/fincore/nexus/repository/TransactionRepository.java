package com.fincore.nexus.repository;

import com.fincore.nexus.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Page<Transaction> findByAccountIdOrderByTimestampDesc(UUID accountId, Pageable pageable);

    List<Transaction> findByAccountIdAndTimestampBetweenOrderByTimestampDesc(UUID accountId, Instant start, Instant end);

    Page<Transaction> findByAccountIdAndTimestampBetweenOrderByTimestampDesc(UUID accountId, Instant start, Instant end, Pageable pageable);

    Optional<Transaction> findByReferenceId(String referenceId);
}
