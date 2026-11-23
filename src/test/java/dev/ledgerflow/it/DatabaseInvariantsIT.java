package dev.ledgerflow.it;

import dev.ledgerflow.application.AccountView;
import dev.ledgerflow.application.Commands;
import dev.ledgerflow.application.JournalEntryView;
import dev.ledgerflow.domain.AccountType;
import dev.ledgerflow.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The schema defends the ledger invariants on its own, even against writes that bypass the domain model.
 */
class DatabaseInvariantsIT extends AbstractIntegrationTest {

    @Autowired
    TransactionTemplate tx;

    @Test
    void unbalancedEntriesAreRejectedAtCommit() {
        AccountView a = openAccount(AccountType.ASSET, "USD", true);
        AccountView b = openAccount(AccountType.LIABILITY, "USD", true);
        UUID entryId = UUID.randomUUID();

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            jdbc.update("insert into journal_entries (id, type, created_at) values (?, 'MANUAL', now())", entryId);
            jdbc.update("""
                    insert into postings (entry_id, account_id, direction, amount_minor, currency, balance_after)
                    values (?, ?, 'DEBIT', 100, 'USD', 0), (?, ?, 'CREDIT', 99, 'USD', 0)""",
                    entryId, a.id(), entryId, b.id());
        })).hasStackTraceContaining("is not balanced");

        assertThat(jdbc.queryForObject("select count(*) from journal_entries where id = ?", Integer.class, entryId))
                .isZero();
    }

    @Test
    void entriesWithoutPostingsAreRejectedAtCommit() {
        UUID entryId = UUID.randomUUID();
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> jdbc.update(
                "insert into journal_entries (id, type, created_at) values (?, 'MANUAL', now())", entryId)))
                .hasStackTraceContaining("at least two postings");
    }

    @Test
    void postedHistoryIsAppendOnly() {
        AccountView alice = fundedWallet("USD", 500);
        AccountView bob = openAccount(AccountType.LIABILITY, "USD", false);
        JournalEntryView entry = transferService.transfer("t", UUID.randomUUID().toString(),
                new Commands.Transfer(alice.id(), bob.id(), 100, "USD", null)).body();

        assertThatThrownBy(() -> jdbc.update("update postings set amount_minor = 1 where entry_id = ?", entry.id()))
                .hasStackTraceContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from journal_entries where id = ?", entry.id()))
                .hasStackTraceContaining("append-only");
    }

    @Test
    void balancesCannotGoNegativeUnlessAllowed() {
        AccountView wallet = openAccount(AccountType.LIABILITY, "USD", false);
        assertThatThrownBy(() -> jdbc.update("update accounts set balance_minor = -1 where id = ?", wallet.id()))
                .hasStackTraceContaining("accounts_balance_non_negative");
    }
}
