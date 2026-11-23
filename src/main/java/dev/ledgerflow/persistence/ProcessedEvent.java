package dev.ledgerflow.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEvent.Key.class)
public class ProcessedEvent {

    @Id
    @Column(nullable = false, length = 128)
    private String consumer;

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
    }

    public String getConsumer() {
        return consumer;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public record Key(String consumer, UUID eventId) implements Serializable {
    }
}
