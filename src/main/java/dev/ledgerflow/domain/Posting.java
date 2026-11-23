package dev.ledgerflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.util.Currency;
import java.util.UUID;

/**
 * One leg of a journal entry. Records the account balance right after it was applied, which makes every
 * statement line self-describing and auditable.
 */
@Entity
@Immutable
@Table(name = "postings")
public class Posting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id", nullable = false)
    private JournalEntry entry;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private Direction direction;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private Currency currency;

    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    protected Posting() {
    }

    Posting(JournalEntry entry, PostingLine line, long balanceAfter) {
        this.entry = entry;
        this.accountId = line.accountId();
        this.direction = line.direction();
        this.amountMinor = line.amountMinor();
        this.currency = line.currency();
        this.balanceAfter = balanceAfter;
    }

    public Long getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public Direction getDirection() {
        return direction;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public Currency getCurrency() {
        return currency;
    }

    public long getBalanceAfter() {
        return balanceAfter;
    }

    public Money amount() {
        return new Money(amountMinor, currency);
    }

    PostingLine toLine() {
        return new PostingLine(accountId, direction, amountMinor, currency);
    }
}
