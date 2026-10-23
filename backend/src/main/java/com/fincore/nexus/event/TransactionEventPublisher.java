package com.fincore.nexus.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

@Component
public class TransactionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventPublisher.class);

    private final Optional<KafkaTemplate<String, Object>> kafkaTemplate;
    private final String topicName;
    private final boolean kafkaEnabled;

    // In-memory list to verify events during testing / local diagnostics
    private final List<TransactionCompletedEvent> publishedEvents = new CopyOnWriteArrayList<>();

    public TransactionEventPublisher(
            Optional<KafkaTemplate<String, Object>> kafkaTemplate,
            @Value("${banking.kafka.topic.transaction-completed:banking.transactions.completed}") String topicName,
            @Value("${banking.kafka.enabled:false}") boolean kafkaEnabled) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicName = topicName;
        this.kafkaEnabled = kafkaEnabled;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTransactionCompleted(TransactionCompletedEvent event) {
        log.info("Transaction committed successfully. Preparing to dispatch event for txnId: {}, account: {}",
                event.transactionId(), event.accountNumber());

        publishedEvents.add(event);

        if (!kafkaEnabled || kafkaTemplate.isEmpty()) {
            log.info("Kafka publishing is disabled or unconfigured. Event stored locally in memory: {}", event.transactionId());
            return;
        }

        try {
            kafkaTemplate.get().send(topicName, event.accountId().toString(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to send TransactionCompletedEvent to Kafka for txnId: {}", event.transactionId(), ex);
                        } else {
                            log.info("TransactionCompletedEvent published successfully to topic: {} partition: {} offset: {}",
                                    topicName,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception ex) {
            // Under NO circumstance should Kafka connection failure rollback or break the committed banking transaction
            log.error("Safe fallback: Exception while submitting event to Kafka broker for txn: {}", event.transactionId(), ex);
        }
    }

    public List<TransactionCompletedEvent> getPublishedEvents() {
        return publishedEvents;
    }

    public void clearPublishedEvents() {
        publishedEvents.clear();
    }
}
