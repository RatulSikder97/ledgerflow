package dev.ledgerflow.api;

import dev.ledgerflow.application.Commands;
import dev.ledgerflow.domain.AccountType;
import dev.ledgerflow.domain.Direction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Request bodies. Amounts are always integers in the currency's minor unit.
 */
public final class Requests {

    private static final String CURRENCY = "[A-Z]{3}";

    private Requests() {
    }

    public record OpenAccount(
            @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9._:-]+") String code,
            @NotBlank @Size(max = 200) String name,
            @NotNull AccountType type,
            @NotNull @Pattern(regexp = CURRENCY) String currency,
            @Schema(description = "Permit the balance to go below zero on the account's normal side")
            boolean allowNegative) {

        Commands.OpenAccount toCommand() {
            return new Commands.OpenAccount(code, name, type, currency, allowNegative);
        }
    }

    public record Transfer(
            @NotNull UUID sourceAccountId,
            @NotNull UUID destinationAccountId,
            @Schema(description = "Amount in minor units, e.g. 1050 = 10.50 USD", example = "1050")
            @Positive long amountMinor,
            @NotNull @Pattern(regexp = CURRENCY) String currency,
            @Size(max = 500) String description) {

        Commands.Transfer toCommand() {
            return new Commands.Transfer(sourceAccountId, destinationAccountId, amountMinor, currency, description);
        }
    }

    public record PostEntry(
            @Size(max = 500) String description,
            @NotNull @Size(min = 2, max = 100) List<@Valid @NotNull Line> postings) {

        public record Line(
                @NotNull UUID accountId,
                @NotNull Direction direction,
                @Positive long amountMinor,
                @NotNull @Pattern(regexp = CURRENCY) String currency) {
        }

        Commands.PostEntry toCommand() {
            return new Commands.PostEntry(description, postings.stream()
                    .map(l -> new Commands.PostEntry.Line(l.accountId(), l.direction(), l.amountMinor(), l.currency()))
                    .toList());
        }
    }

    public record Reverse(@Size(max = 500) String reason) {
    }
}
