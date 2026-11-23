package dev.ledgerflow.application;

import dev.ledgerflow.domain.AccountType;
import dev.ledgerflow.domain.Direction;

import java.util.List;
import java.util.UUID;

/**
 * Input to the application services, already syntactically validated by the API layer.
 */
public final class Commands {

    private Commands() {
    }

    public record OpenAccount(String code, String name, AccountType type, String currency, boolean allowNegative) {
    }

    public record Transfer(UUID sourceAccountId, UUID destinationAccountId, long amountMinor, String currency,
                           String description) {
    }

    public record PostEntry(String description, List<Line> postings) {

        public record Line(UUID accountId, Direction direction, long amountMinor, String currency) {
        }
    }

    public record Reverse(UUID entryId, String reason) {
    }
}
