package dev.ledgerflow.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class KafkaConfig {

    @Bean
    NewTopic journalEntriesTopic(LedgerflowProperties properties) {
        return TopicBuilder.name(properties.topic()).partitions(properties.topicPartitions()).replicas(1).build();
    }

    @Bean
    NewTopic journalEntriesDeadLetterTopic(LedgerflowProperties properties) {
        return TopicBuilder.name(deadLetterTopic(properties)).partitions(properties.topicPartitions()).replicas(1)
                .build();
    }

    /**
     * Retries transient failures with backoff, then parks the record on the dead-letter topic so one poison
     * message cannot stall its partition. Boot wires this into the listener container factory.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaOperations<?, ?> kafka, LedgerflowProperties properties) {
        var recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, ex) -> new TopicPartition(deadLetterTopic(properties), record.partition()));
        var backOff = new ExponentialBackOff(200, 2.0);
        backOff.setMaxElapsedTime(5_000);
        return new DefaultErrorHandler(recoverer, backOff);
    }

    static String deadLetterTopic(LedgerflowProperties properties) {
        return properties.topic() + ".DLT";
    }
}
