package dev.ledgerflow.application;

import dev.ledgerflow.domain.event.JournalEntryPosted;
import dev.ledgerflow.persistence.ProcessedEventRepository;
import dev.ledgerflow.persistence.StatementLineRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds account statements from ledger events. The processed-event marker and the statement lines commit in
 * one transaction, so a redelivered event is detected and skipped: exactly-once effect.
 */
@Service
public class StatementProjector {

    public static final String CONSUMER = "statement-projector";

    private static final Logger log = LoggerFactory.getLogger(StatementProjector.class);

    private final ProcessedEventRepository processed;
    private final StatementLineRepository lines;
    private final MeterRegistry meterRegistry;

    public StatementProjector(ProcessedEventRepository processed, StatementLineRepository lines,
                              MeterRegistry meterRegistry) {
        this.processed = processed;
        this.lines = lines;
        this.meterRegistry = meterRegistry;
    }

    /**
     * @return true if the event changed the projection, false if it was a duplicate
     */
    @Transactional
    public boolean apply(JournalEntryPosted event) {
        if (processed.markProcessed(CONSUMER, event.eventId()) == 0) {
            log.debug("Skipping duplicate event {} for entry {}", event.eventId(), event.entryId());
            meterRegistry.counter("ledgerflow.projection.events", "result", "duplicate").increment();
            return false;
        }
        for (JournalEntryPosted.PostingPayload p : event.postings()) {
            lines.insertIfAbsent(p.postingId(), p.accountId(), event.entryId(), event.entryType(), p.direction(),
                    p.amountMinor(), p.currency(), p.balanceAfter(), event.description(), event.occurredAt());
        }
        meterRegistry.counter("ledgerflow.projection.events", "result", "applied").increment();
        return true;
    }
}
