package dev.ledgerflow.domain.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Integration event published (via the outbox) for every committed journal entry.
 * Postings carry the account balance after the leg was applied, so consumers never need to query the ledger.
 */
public record JournalEntryPosted(
        UUID eventId,
        UUID entryId,
        String entryType,
        String description,
        UUID reversesEntryId,
        Instant occurredAt,
        List<PostingPayload> postings) {

    public static final String TYPE = "ledger.JournalEntryPosted.v1";

    public record PostingPayload(
            long postingId,
            UUID accountId,
            String direction,
            long amountMinor,
            String currency,
            long balanceAfter) {
    }
}
