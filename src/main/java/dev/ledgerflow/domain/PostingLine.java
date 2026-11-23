package dev.ledgerflow.domain;

import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/**
 * One requested leg of a journal entry, before it is applied to an account.
 */
public record PostingLine(UUID accountId, Direction direction, long amountMinor, Currency currency) {

    public PostingLine {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(currency, "currency");
        if (amountMinor <= 0) {
            throw new InvalidEntryException("posting amount must be positive, got " + amountMinor);
        }
    }

    public static PostingLine debit(UUID accountId, Money amount) {
        return new PostingLine(accountId, Direction.DEBIT, amount.amountMinor(), amount.currency());
    }

    public static PostingLine credit(UUID accountId, Money amount) {
        return new PostingLine(accountId, Direction.CREDIT, amount.amountMinor(), amount.currency());
    }

    public long signedAmount() {
        return direction == Direction.DEBIT ? amountMinor : -amountMinor;
    }
}
