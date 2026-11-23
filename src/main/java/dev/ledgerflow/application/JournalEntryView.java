package dev.ledgerflow.application;

import dev.ledgerflow.domain.Direction;
import dev.ledgerflow.domain.EntryType;
import dev.ledgerflow.domain.JournalEntry;
import dev.ledgerflow.domain.Money;
import dev.ledgerflow.domain.Posting;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JournalEntryView(
        UUID id,
        EntryType type,
        String description,
        UUID reversesEntryId,
        Instant createdAt,
        List<PostingView> postings) {

    public record PostingView(long id, UUID accountId, Direction direction, MoneyView amount, MoneyView balanceAfter) {

        static PostingView of(Posting p) {
            return new PostingView(p.getId(), p.getAccountId(), p.getDirection(), MoneyView.of(p.amount()),
                    MoneyView.of(new Money(p.getBalanceAfter(), p.getCurrency())));
        }
    }

    public static JournalEntryView of(JournalEntry entry) {
        return new JournalEntryView(entry.getId(), entry.getType(), entry.getDescription(),
                entry.getReversesEntryId(), entry.getCreatedAt(),
                entry.getPostings().stream().map(PostingView::of).toList());
    }
}
