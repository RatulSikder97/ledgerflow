package dev.ledgerflow.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JournalEntryTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final Account cash = Account.open("cash", "Cash", AccountType.ASSET, USD, false, NOW);
    private final Account alice = Account.open("alice", "Alice", AccountType.LIABILITY, USD, false, NOW);
    private final Account bob = Account.open("bob", "Bob", AccountType.LIABILITY, USD, false, NOW);

    private static Map<UUID, Account> index(Account... accounts) {
        return Stream.of(accounts).collect(Collectors.toMap(Account::getId, Function.identity()));
    }

    private static PostingLine debit(Account a, long amount) {
        return PostingLine.debit(a.getId(), new Money(amount, a.getCurrency()));
    }

    private static PostingLine credit(Account a, long amount) {
        return PostingLine.credit(a.getId(), new Money(amount, a.getCurrency()));
    }

    @Test
    void appliesPostingsOnEachAccountsNormalSide() {
        JournalEntry deposit = JournalEntry.post(EntryType.TRANSFER, "deposit",
                List.of(debit(cash, 10_000), credit(alice, 10_000)), index(cash, alice), NOW);

        assertThat(cash.balance()).isEqualTo(new Money(10_000, USD));
        assertThat(alice.balance()).isEqualTo(new Money(10_000, USD));
        assertThat(deposit.getPostings()).extracting(Posting::getBalanceAfter).containsExactly(10_000L, 10_000L);
    }

    @Test
    void rejectsUnbalancedEntries() {
        assertThatThrownBy(() -> JournalEntry.requireBalanced(List.of(debit(cash, 100), credit(alice, 99))))
                .isInstanceOf(UnbalancedEntryException.class)
                .hasMessageContaining("USD debits-credits=1");
    }

    @Test
    void rejectsSingleLegEntries() {
        assertThatThrownBy(() -> JournalEntry.requireBalanced(List.of(debit(cash, 100))))
                .isInstanceOf(UnbalancedEntryException.class);
    }

    @Test
    void balancesPerCurrencyNotInAggregate() {
        // 100 USD debit vs 100 EUR credit sums to zero numerically but is not balanced.
        UUID any = UUID.randomUUID();
        List<PostingLine> lines = List.of(
                new PostingLine(any, Direction.DEBIT, 100, USD),
                new PostingLine(any, Direction.CREDIT, 100, EUR));
        assertThatThrownBy(() -> JournalEntry.requireBalanced(lines)).isInstanceOf(UnbalancedEntryException.class);
    }

    @Test
    void acceptsMultiCurrencyEntryBalancedPerCurrency() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        JournalEntry.requireBalanced(List.of(
                new PostingLine(a, Direction.DEBIT, 100, USD),
                new PostingLine(b, Direction.CREDIT, 100, USD),
                new PostingLine(a, Direction.DEBIT, 92, EUR),
                new PostingLine(b, Direction.CREDIT, 92, EUR)));
    }

    @Test
    void rejectsOverdraftOnAccountsThatDoNotAllowIt() {
        JournalEntry.post(EntryType.TRANSFER, null, List.of(debit(cash, 500), credit(alice, 500)),
                index(cash, alice), NOW);

        assertThatThrownBy(() -> JournalEntry.post(EntryType.TRANSFER, null,
                List.of(debit(alice, 501), credit(bob, 501)), index(alice, bob), NOW))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void judgesLimitsOnTheNetEffectOfTheWholeEntry() {
        // Alice is debited before she is credited within the same entry; only the net result matters.
        JournalEntry.post(EntryType.MANUAL, null,
                List.of(debit(alice, 300), credit(bob, 300), debit(cash, 300), credit(alice, 300)),
                index(cash, alice, bob), NOW);

        assertThat(alice.balance().amountMinor()).isZero();
        assertThat(bob.balance().amountMinor()).isEqualTo(300);
    }

    @Test
    void rejectsPostingInAnotherCurrencyThanTheAccount() {
        Account euroCash = Account.open("eur-cash", "EUR cash", AccountType.ASSET, EUR, false, NOW);
        List<PostingLine> lines = List.of(
                new PostingLine(euroCash.getId(), Direction.DEBIT, 100, USD),
                new PostingLine(alice.getId(), Direction.CREDIT, 100, USD));

        assertThatThrownBy(() -> JournalEntry.post(EntryType.MANUAL, null, lines, index(euroCash, alice), NOW))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void failsForUnknownAccounts() {
        assertThatThrownBy(() -> JournalEntry.post(EntryType.TRANSFER, null,
                List.of(debit(cash, 1), credit(alice, 1)), index(cash), NOW))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void reversalMirrorsEveryPostingAndRestoresBalances() {
        JournalEntry deposit = JournalEntry.post(EntryType.TRANSFER, "deposit",
                List.of(debit(cash, 700), credit(alice, 700)), index(cash, alice), NOW);

        JournalEntry reversal = deposit.reverse("customer dispute", index(cash, alice), NOW);

        assertThat(reversal.getType()).isEqualTo(EntryType.REVERSAL);
        assertThat(reversal.getReversesEntryId()).isEqualTo(deposit.getId());
        assertThat(reversal.getPostings()).extracting(Posting::getDirection)
                .containsExactly(Direction.CREDIT, Direction.DEBIT);
        assertThat(cash.balance().amountMinor()).isZero();
        assertThat(alice.balance().amountMinor()).isZero();
    }

    @Test
    void aReversalCannotBeReversed() {
        JournalEntry deposit = JournalEntry.post(EntryType.TRANSFER, null,
                List.of(debit(cash, 1), credit(alice, 1)), index(cash, alice), NOW);
        JournalEntry reversal = deposit.reverse(null, index(cash, alice), NOW);

        assertThatThrownBy(() -> reversal.reverse(null, index(cash, alice), NOW))
                .isInstanceOf(InvalidEntryException.class);
    }

    @Test
    void reversalEntriesCannotBePostedDirectly() {
        assertThatThrownBy(() -> JournalEntry.post(EntryType.REVERSAL, null,
                List.of(debit(cash, 1), credit(alice, 1)), index(cash, alice), NOW))
                .isInstanceOf(InvalidEntryException.class);
    }

    @Test
    void postingAmountsMustBePositive() {
        assertThatThrownBy(() -> new PostingLine(UUID.randomUUID(), Direction.DEBIT, 0, USD))
                .isInstanceOf(InvalidEntryException.class);
    }
}
