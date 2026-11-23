package dev.ledgerflow.it;

import dev.ledgerflow.application.AccountView;
import dev.ledgerflow.application.Commands;
import dev.ledgerflow.domain.Account;
import dev.ledgerflow.domain.EntryType;
import dev.ledgerflow.domain.InsufficientFundsException;
import dev.ledgerflow.domain.JournalEntry;
import dev.ledgerflow.domain.PostingLine;
import dev.ledgerflow.domain.Money;
import dev.ledgerflow.persistence.AccountRepository;
import dev.ledgerflow.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferConcurrencyIT extends AbstractIntegrationTest {

    private static final int ACCOUNTS = 5;
    private static final long INITIAL = 100_000;
    private static final int TRANSFERS = 400;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    TransactionTemplate tx;

    @Test
    void concurrentTransfersNeverLoseUpdatesOrDeadlock() throws Exception {
        List<UUID> wallets = new ArrayList<>();
        for (int i = 0; i < ACCOUNTS; i++) {
            wallets.add(fundedWallet("USD", INITIAL).id());
        }

        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < TRANSFERS; i++) {
                futures.add(pool.submit(() -> {
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    int from = random.nextInt(ACCOUNTS);
                    int to = (from + 1 + random.nextInt(ACCOUNTS - 1)) % ACCOUNTS;
                    long amount = 1 + random.nextInt(5_000);
                    start.await();
                    try {
                        transferService.transfer("load-test", UUID.randomUUID().toString(),
                                new Commands.Transfer(wallets.get(from), wallets.get(to), amount, "USD", null));
                        succeeded.incrementAndGet();
                    } catch (InsufficientFundsException e) {
                        rejected.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
        }

        assertThat(succeeded.get() + rejected.get()).isEqualTo(TRANSFERS);
        assertThat(succeeded.get()).isGreaterThan(TRANSFERS / 2);

        long total = 0;
        for (UUID wallet : wallets) {
            long balance = balanceOf(wallet);
            assertThat(balance).isNotNegative();
            // The cached balance must equal the sum of the account's postings: no lost update.
            Long fromPostings = jdbc.queryForObject("""
                    select coalesce(sum(case direction when 'CREDIT' then amount_minor else -amount_minor end), 0)
                    from postings where account_id = ?""", Long.class, wallet);
            assertThat(balance).isEqualTo(fromPostings);
            total += balance;
        }
        // Money is only moved between the wallets, never created or destroyed.
        assertThat(total).isEqualTo(ACCOUNTS * INITIAL);

        // Every account's version advanced once per posting that touched it.
        for (UUID wallet : wallets) {
            Integer postings = jdbc.queryForObject("select count(*) from postings where account_id = ?",
                    Integer.class, wallet);
            assertThat(accountService.get(wallet).version()).isEqualTo(postings.longValue());
        }
    }

    @Test
    void staleWritesAreRejectedByOptimisticLocking() {
        AccountView wallet = fundedWallet("USD", 1_000);
        AccountView other = openAccount(dev.ledgerflow.domain.AccountType.LIABILITY, "USD", false);

        // Load a copy, then let a regular transfer change the row underneath it.
        Account stale = tx.execute(s -> accountRepository.findById(wallet.id()).orElseThrow());
        Account target = tx.execute(s -> accountRepository.findById(other.id()).orElseThrow());
        transferService.transfer("t", UUID.randomUUID().toString(),
                new Commands.Transfer(wallet.id(), other.id(), 100, "USD", null));

        // Writing the stale copy back (as a buggy path without row locks would) must fail, not overwrite.
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            JournalEntry.post(EntryType.TRANSFER, null,
                    List.of(PostingLine.debit(wallet.id(), Money.of(50, "USD")),
                            PostingLine.credit(other.id(), Money.of(50, "USD"))),
                    Map.of(stale.getId(), stale, target.getId(), target), Instant.now());
            accountRepository.saveAndFlush(stale);
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(balanceOf(wallet.id())).isEqualTo(900);
    }
}
