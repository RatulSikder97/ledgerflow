package dev.ledgerflow.domain;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * An amount in the currency's minor unit (cents, pence, yen...). Arithmetic is exact {@code long} math and
 * fails loudly on overflow; there is no floating point anywhere in the ledger.
 */
public record Money(long amountMinor, Currency currency) {

    public Money {
        Objects.requireNonNull(currency, "currency");
    }

    public static Money of(long amountMinor, String currencyCode) {
        return new Money(amountMinor, currency(currencyCode));
    }

    public static Money zero(Currency currency) {
        return new Money(0, currency);
    }

    /**
     * Resolves an ISO 4217 code, rejecting codes the JDK does not know.
     */
    public static Currency currency(String code) {
        if (code == null || !code.matches("[A-Z]{3}")) {
            throw new InvalidEntryException("currency must be an ISO 4217 code, got '" + code + "'");
        }
        try {
            return Currency.getInstance(code);
        } catch (IllegalArgumentException e) {
            throw new InvalidEntryException("unknown currency '" + code + "'");
        }
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(amountMinor, other.amountMinor), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(amountMinor, other.amountMinor), currency);
    }

    public boolean isNegative() {
        return amountMinor < 0;
    }

    /**
     * Human-readable decimal form for display only, e.g. 12345 USD -> "123.45", 500 JPY -> "500".
     */
    public String toDecimalString() {
        int scale = Math.max(currency.getDefaultFractionDigits(), 0);
        return BigDecimal.valueOf(amountMinor, scale).toPlainString();
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    @Override
    public String toString() {
        return toDecimalString() + " " + currency.getCurrencyCode();
    }
}
