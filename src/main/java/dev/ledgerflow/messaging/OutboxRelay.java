package dev.ledgerflow.messaging;

import dev.ledgerflow.config.LedgerflowProperties;
import dev.ledgerflow.persistence.OutboxEvent;
import dev.ledgerflow.persistence.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Drains the transactional outbox into Kafka.
 * <p>
 * Each poll locks a batch with {@code FOR UPDATE SKIP LOCKED}, publishes synchronously (acks=all, idempotent
 * producer) and marks the rows published in the same transaction. A crash between send and commit re-sends the
 * batch, so delivery is at-least-once; consumers deduplicate by event id.
 */
@Component
@ConditionalOnProperty(value = "ledgerflow.outbox.relay-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate tx;
    private final LedgerflowProperties properties;
    private final Clock clock;
    private final Counter published;
    private final Counter failures;
    private final AtomicLong pending = new AtomicLong();

    public OutboxRelay(OutboxEventRepository outbox, KafkaTemplate<String, String> kafka, TransactionTemplate tx,
                       LedgerflowProperties properties, Clock clock, MeterRegistry meterRegistry) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.tx = tx;
        this.properties = properties;
        this.clock = clock;
        this.published = Counter.builder("ledgerflow.outbox.published").register(meterRegistry);
        this.failures = Counter.builder("ledgerflow.outbox.publish.failures").register(meterRegistry);
        Gauge.builder("ledgerflow.outbox.pending", pending, AtomicLong::get)
                .description("Unpublished outbox events after the last poll")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${ledgerflow.outbox.poll-interval:250ms}")
    public void poll() {
        int sent;
        do {
            sent = tx.execute(status -> publishBatch());
        } while (sent == properties.outbox().batchSize());
        pending.set(outbox.countByPublishedAtIsNull());
    }

    /**
     * @return number of events published in this batch
     */
    int publishBatch() {
        List<OutboxEvent> batch = outbox.lockNextBatch(properties.outbox().batchSize());
        int sent = 0;
        for (OutboxEvent event : batch) {
            try {
                var record = new ProducerRecord<>(properties.topic(), event.getAggregateId().toString(),
                        event.getPayload());
                record.headers().add("event-type", event.getEventType().getBytes(StandardCharsets.UTF_8));
                record.headers().add("event-id", event.getId().toString().getBytes(StandardCharsets.UTF_8));
                kafka.send(record).get(properties.outbox().sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
                event.markPublished(clock.instant());
                published.increment();
                sent++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                event.markFailedAttempt();
                failures.increment();
                break;
            } catch (Exception e) {
                // Stop at the first failure to preserve creation order; the rest of the batch is retried next poll.
                log.warn("Publishing outbox event {} failed (attempt {})", event.getId(), event.getAttempts() + 1, e);
                event.markFailedAttempt();
                failures.increment();
                break;
            }
        }
        return sent;
    }
}
