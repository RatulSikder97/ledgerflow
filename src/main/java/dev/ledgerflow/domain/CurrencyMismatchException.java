package dev.ledgerflow.domain;

import java.util.Currency;

public class CurrencyMismatchException extends LedgerException.RuleViolation {

    public CurrencyMismatchException(Currency expected, Currency actual) {
        super("currency mismatch: expected " + expected.getCurrencyCode() + " but got " + actual.getCurrencyCode());
    }

    @Override
    public String problemType() {
        return "currency-mismatch";
    }
}
