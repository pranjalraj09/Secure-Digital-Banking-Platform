package com.fincore.nexus.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "banking.kafka.enabled", havingValue = "true")
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    @Value("${banking.kafka.topic.transaction-completed:banking.transactions.completed}")
    private String transactionCompletedTopic;

    @Bean
    public NewTopic transactionCompletedTopic() {
        log.info("Creating Kafka topic: {}", transactionCompletedTopic);
        return TopicBuilder.name(transactionCompletedTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
