package dev.ledgerflow.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ledgerflow.domain.Account;
import dev.ledgerflow.domain.EntryAlreadyReversedException;
import dev.ledgerflow.domain.EntryType;
import dev.ledgerflow.domain.Ids;
import dev.ledgerflow.domain.JournalEntry;
import dev.ledgerflow.domain.JournalEntryNotFoundException;
import dev.ledgerflow.domain.Posting;
import dev.ledgerflow.domain.PostingLine;
import dev.ledgerflow.domain.event.JournalEntryPosted;
import dev.ledgerflow.persistence.AccountRepository;
import dev.ledgerflow.persistence.JournalEntryRepository;
import dev.ledgerflow.persistence.OutboxEvent;
import dev.ledgerflow.persistence.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The single write path into the ledger: lock accounts, build the entry in the domain, persist it and append
 * the integration event to the outbox, all inside the caller's transaction.
 */
@Component
public class LedgerPoster {

    static final String AGGREGATE_TYPE = "JournalEntry";

    private final AccountRepository accounts;
    private final JournalEntryRepository entries;
    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public LedgerPoster(AccountRepository accounts, JournalEntryRepository entries, OutboxEventRepository outbox,
                        ObjectMapper objectMapper, MeterRegistry meterRegistry, Clock clock) {
        this.accounts = accounts;
        this.entries = entries;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public JournalEntryView post(EntryType type, String description, List<PostingLine> lines) {
        JournalEntry.requireBalanced(lines);
        Map<UUID, Account> locked = lock(lines.stream().map(PostingLine::accountId).toList());
        return persist(JournalEntry.post(type, description, lines, locked, clock.instant()));
    }

    /**
     * Posts the mirror image of an existing entry. The "already reversed" check runs after the account locks are
     * held, so two concurrent reversals of the same entry serialise and the loser sees the winner's row (the
     * unique index on reverses_entry_id is the backstop).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public JournalEntryView postReversal(UUID entryId, String reason) {
        JournalEntry original = entries.findWithPostingsById(entryId)
                .orElseThrow(() -> new JournalEntryNotFoundException(entryId));
        Map<UUID, Account> locked = lock(original.accountIds());
        if (entries.existsByReversesEntryId(entryId)) {
            throw new EntryAlreadyReversedException(entryId);
        }
        return persist(original.reverse(reason, locked, clock.instant()));
    }

    private Map<UUID, Account> lock(Collection<UUID> ids) {
        TreeSet<UUID> distinct = new TreeSet<>(ids);
        return accounts.findAllByIdForUpdate(distinct).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
    }

    private JournalEntryView persist(JournalEntry entry) {
        entries.save(entry);
        // IDENTITY ids are assigned on insert; flush so postings carry their ids into the event.
        entries.flush();
        outbox.save(new OutboxEvent(Ids.newId(), AGGREGATE_TYPE, entry.getId(), JournalEntryPosted.TYPE,
                toJson(toEvent(entry)), entry.getCreatedAt()));
        meterRegistry.counter("ledgerflow.entries.posted", "type", entry.getType().name()).increment();
        return JournalEntryView.of(entry);
    }

    private JournalEntryPosted toEvent(JournalEntry entry) {
        List<JournalEntryPosted.PostingPayload> postings = entry.getPostings().stream()
                .map(LedgerPoster::toPayload)
                .toList();
        return new JournalEntryPosted(Ids.newId(), entry.getId(), entry.getType().name(), entry.getDescription(),
                entry.getReversesEntryId(), entry.getCreatedAt(), postings);
    }

    private static JournalEntryPosted.PostingPayload toPayload(Posting p) {
        return new JournalEntryPosted.PostingPayload(p.getId(), p.getAccountId(), p.getDirection().name(),
                p.getAmountMinor(), p.getCurrency().getCurrencyCode(), p.getBalanceAfter());
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialise event", e);
        }
    }
}
