package dev.ledgerflow.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Locks the oldest unpublished events. SKIP LOCKED lets several application instances drain the outbox
     * concurrently without blocking on, or double-sending, each other's batches.
     */
    @Query(nativeQuery = true, value = """
            select * from outbox_events
            where published_at is null
            order by created_at
            limit :batchSize
            for update skip locked
            """)
    List<OutboxEvent> lockNextBatch(int batchSize);

    long countByPublishedAtIsNull();
}
