package dev.ledgerflow.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * An immutable, balanced set of postings. Entries are never updated or deleted: a mistake is corrected by
 * posting a {@linkplain #reverse reversal}. The database enforces the same rules (deferred balance trigger,
 * append-only triggers), so the invariant holds even for writes that bypass this class.
 */
@Entity
@Immutable
@Table(name = "journal_entries")
public class JournalEntry implements Persistable<UUID> {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EntryType type;

    @Column(length = 500)
    private String description;

    @Column(name = "reverses_entry_id", unique = true)
    private UUID reversesEntryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "entry", cascade = CascadeType.PERSIST)
    @OrderBy("id")
    private List<Posting> postings = new ArrayList<>();

    @Transient
    private boolean isNew = true;

    protected JournalEntry() {
    }

    private JournalEntry(EntryType type, String description, UUID reversesEntryId, Instant createdAt) {
        this.id = Ids.newId();
        this.type = type;
        this.description = description;
        this.reversesEntryId = reversesEntryId;
        this.createdAt = createdAt.truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * Validates the lines, applies them to the (already locked) accounts and returns the new entry.
     *
     * @param accounts every account referenced by {@code lines}, keyed by id
     */
    public static JournalEntry post(EntryType type, String description, List<PostingLine> lines,
                                    Map<UUID, Account> accounts, Instant now) {
        if (type == EntryType.REVERSAL) {
            throw new InvalidEntryException("reversal entries are created with JournalEntry#reverse");
        }
        return create(type, description, null, lines, accounts, now);
    }

    /**
     * Creates the mirror image of this entry: same amounts, opposite directions.
     */
    public JournalEntry reverse(String reason, Map<UUID, Account> accounts, Instant now) {
        if (type == EntryType.REVERSAL) {
            throw new InvalidEntryException("a reversal entry cannot itself be reversed; post a new entry instead");
        }
        List<PostingLine> mirrored = postings.stream()
                .map(Posting::toLine)
                .map(l -> new PostingLine(l.accountId(), l.direction().opposite(), l.amountMinor(), l.currency()))
                .toList();
        String text = reason != null ? reason : "Reversal of " + id;
        return create(EntryType.REVERSAL, text, id, mirrored, accounts, now);
    }

    /**
     * Double-entry rule: at least two legs, and for every currency the debits equal the credits.
     */
    public static void requireBalanced(List<PostingLine> lines) {
        if (lines == null || lines.size() < 2) {
            throw new UnbalancedEntryException("an entry needs at least two postings");
        }
        Map<Currency, Long> net = new TreeMap<>((a, b) -> a.getCurrencyCode().compareTo(b.getCurrencyCode()));
        for (PostingLine line : lines) {
            net.merge(line.currency(), line.signedAmount(), Math::addExact);
        }
        String unbalanced = net.entrySet().stream()
                .filter(e -> e.getValue() != 0)
                .map(e -> e.getKey().getCurrencyCode() + " debits-credits=" + e.getValue())
                .collect(Collectors.joining(", "));
        if (!unbalanced.isEmpty()) {
            throw new UnbalancedEntryException(unbalanced);
        }
    }

    private static JournalEntry create(EntryType type, String description, UUID reverses, List<PostingLine> lines,
                                       Map<UUID, Account> accounts, Instant now) {
        requireBalanced(lines);
        JournalEntry entry = new JournalEntry(type, description, reverses, now);
        Set<Account> touched = new LinkedHashSet<>();
        for (PostingLine line : lines) {
            Account account = accounts.get(line.accountId());
            if (account == null) {
                throw new AccountNotFoundException(line.accountId());
            }
            long balanceAfter = account.apply(line.direction(), line.amountMinor(), line.currency());
            entry.postings.add(new Posting(entry, line, balanceAfter));
            touched.add(account);
        }
        touched.forEach(Account::assertWithinLimits);
        return entry;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    public EntryType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public UUID getReversesEntryId() {
        return reversesEntryId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<Posting> getPostings() {
        return Collections.unmodifiableList(postings);
    }

    /** Distinct account ids referenced by this entry. */
    public Set<UUID> accountIds() {
        return postings.stream().map(Posting::getAccountId).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
