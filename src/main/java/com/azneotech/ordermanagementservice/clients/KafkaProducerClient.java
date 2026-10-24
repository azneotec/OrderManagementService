package com.azneotech.ordermanagementservice.clients;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
public class KafkaProducerClient {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaProducerClient(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Best-effort publish for messages that must not affect the caller's outcome. When a transaction is
     * active the message is sent only once it has committed (nothing is sent if it rolls back); otherwise
     * it is sent immediately. A Kafka failure is logged, never thrown. The key (the order number) keeps all
     * messages of one order on one partition, so they are consumed in the order they were sent.
     */
    public void sendAfterCommit(String topic, String key, String message) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendQuietly(topic, key, message);
                }
            });
        } else {
            sendQuietly(topic, key, message);
        }
    }

    private void sendQuietly(String topic, String key, String message) {
        try {
            kafkaTemplate.send(topic, key, message).whenComplete((result, failure) -> {
                if (failure != null) {
                    log.error("Could not publish message for {} to Kafka topic '{}'", key, topic, failure);
                }
            });
        } catch (RuntimeException e) {
            log.error("Could not publish message for {} to Kafka topic '{}'", key, topic, e);
        }
    }

}
