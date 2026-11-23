package dev.ledgerflow.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-model row: one line of an account statement, projected from {@code JournalEntryPosted} events.
 */
@Entity
@Immutable
@Table(name = "statement_lines")
public class StatementLine {

    @Id
    @Column(name = "posting_id")
    private Long postingId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "entry_id", nullable = false)
    private UUID entryId;

    @Column(name = "entry_type", nullable = false, length = 16)
    private String entryType;

    @Column(nullable = false, length = 6)
    private String direction;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    @Column(length = 500)
    private String description;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected StatementLine() {
    }

    public Long getPostingId() {
        return postingId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getEntryId() {
        return entryId;
    }

    public String getEntryType() {
        return entryType;
    }

    public String getDirection() {
        return direction;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public long getBalanceAfter() {
        return balanceAfter;
    }

    public String getDescription() {
        return description;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
