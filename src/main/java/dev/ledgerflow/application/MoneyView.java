package dev.ledgerflow.application;

import dev.ledgerflow.domain.Money;

/**
 * Wire representation of money: the exact integer minor-unit amount plus a display-only decimal string.
 */
public record MoneyView(long amountMinor, String currency, String amount) {

    public static MoneyView of(Money money) {
        return new MoneyView(money.amountMinor(), money.currency().getCurrencyCode(), money.toDecimalString());
    }
}
