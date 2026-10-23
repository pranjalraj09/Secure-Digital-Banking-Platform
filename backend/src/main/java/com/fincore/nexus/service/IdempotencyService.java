package com.fincore.nexus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincore.nexus.entity.IdempotencyRecord;
import com.fincore.nexus.exception.IdempotencyConflictException;
import com.fincore.nexus.repository.IdempotencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyRepository idempotencyRepository, ObjectMapper objectMapper) {
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> getRecord(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return idempotencyRepository.findById(key);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveRecord(String key, Object requestPayload, int status, Object responseBody) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            String requestHash = computeHash(requestPayload);
            String serializedResponse = objectMapper.writeValueAsString(responseBody);
            IdempotencyRecord record = new IdempotencyRecord(key, requestHash, status, serializedResponse);
            idempotencyRepository.save(record);
            log.info("Idempotency record saved for key: {}", key);
        } catch (Exception ex) {
            log.warn("Could not save idempotency record for key {}: {}", key, ex.getMessage());
        }
    }

    public void validateRequestHash(IdempotencyRecord record, Object requestPayload) {
        if (record == null) {
            return;
        }
        String currentHash = computeHash(requestPayload);
        if (record.getRequestHash() != null && !record.getRequestHash().equals(currentHash)) {
            throw new IdempotencyConflictException("Idempotency key '" + record.getIdempotencyKey() +
                    "' was already used with a different request payload.");
        }
    }

    private String computeHash(Object obj) {
        if (obj == null) return "";
        try {
            String json = objectMapper.writeValueAsString(obj);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return String.valueOf(obj.hashCode());
        }
    }
}
