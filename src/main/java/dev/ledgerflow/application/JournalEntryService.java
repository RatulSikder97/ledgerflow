package dev.ledgerflow.application;

import dev.ledgerflow.domain.EntryType;
import dev.ledgerflow.domain.JournalEntryNotFoundException;
import dev.ledgerflow.domain.Money;
import dev.ledgerflow.domain.PostingLine;
import dev.ledgerflow.persistence.JournalEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Arbitrary multi-leg (and multi-currency) entries, lookups and reversals.
 */
@Service
public class JournalEntryService {

    static final String OPERATION = "journal-entry";

    private final IdempotencyService idempotency;
    private final LedgerPoster poster;
    private final JournalEntryRepository entries;

    public JournalEntryService(IdempotencyService idempotency, LedgerPoster poster, JournalEntryRepository entries) {
        this.idempotency = idempotency;
        this.poster = poster;
        this.entries = entries;
    }

    @Transactional
    public IdempotentResult<JournalEntryView> post(String clientId, String idempotencyKey,
                                                   Commands.PostEntry command) {
        return idempotency.execute(clientId, idempotencyKey, OPERATION, command, JournalEntryView.class, () -> {
            List<PostingLine> lines = command.postings().stream()
                    .map(l -> new PostingLine(l.accountId(), l.direction(), l.amountMinor(),
                            Money.currency(l.currency())))
                    .toList();
            return poster.post(EntryType.MANUAL, command.description(), lines);
        });
    }

    @Transactional
    public JournalEntryView reverse(Commands.Reverse command) {
        return poster.postReversal(command.entryId(), command.reason());
    }

    @Transactional(readOnly = true)
    public JournalEntryView get(UUID id) {
        return entries.findWithPostingsById(id)
                .map(JournalEntryView::of)
                .orElseThrow(() -> new JournalEntryNotFoundException(id));
    }
}
