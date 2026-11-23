package dev.ledgerflow.application;

import dev.ledgerflow.domain.Money;
import dev.ledgerflow.persistence.StatementLine;

import java.time.Instant;
import java.util.UUID;

public record StatementLineView(
        long postingId,
        UUID entryId,
        String entryType,
        String direction,
        MoneyView amount,
        MoneyView balanceAfter,
        String description,
        Instant occurredAt) {

    static StatementLineView of(StatementLine line) {
        return new StatementLineView(line.getPostingId(), line.getEntryId(), line.getEntryType(),
                line.getDirection(), MoneyView.of(Money.of(line.getAmountMinor(), line.getCurrency())),
                MoneyView.of(Money.of(line.getBalanceAfter(), line.getCurrency())), line.getDescription(),
                line.getOccurredAt());
    }
}
