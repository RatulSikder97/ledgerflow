package dev.ledgerflow.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ledgerflow.application.StatementProjector;
import dev.ledgerflow.domain.event.JournalEntryPosted;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Feeds ledger events into the statement projection. Offsets are committed after the projection transaction,
 * so a crash in between redelivers the event and the processed-event table turns it into a no-op.
 */
@Component
public class JournalEntryEventListener {

    private final StatementProjector projector;
    private final ObjectMapper objectMapper;

    public JournalEntryEventListener(StatementProjector projector, ObjectMapper objectMapper) {
        this.projector = projector;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(id = StatementProjector.CONSUMER, topics = "${ledgerflow.topic:ledger.journal-entries}")
    public void onJournalEntryPosted(String payload) throws JsonProcessingException {
        projector.apply(objectMapper.readValue(payload, JournalEntryPosted.class));
    }
}
