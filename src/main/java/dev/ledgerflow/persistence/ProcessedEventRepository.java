package dev.ledgerflow.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEvent.Key> {

    /**
     * Records that {@code consumer} has handled {@code eventId}. Returns 0 when it already had, which makes the
     * surrounding projection transaction a no-op: exactly-once effect on top of at-least-once delivery.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into processed_events (consumer, event_id, processed_at)
            values (:consumer, :eventId, now())
            on conflict do nothing
            """)
    int markProcessed(String consumer, UUID eventId);

    long countByConsumer(String consumer);
}
