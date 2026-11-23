package dev.ledgerflow.domain;

import java.util.UUID;

public class InsufficientFundsException extends LedgerException.RuleViolation {

    public InsufficientFundsException(UUID accountId, Money resultingBalance) {
        super("account " + accountId + " would be left with a balance of " + resultingBalance
                + " and does not allow a negative balance");
    }

    @Override
    public String problemType() {
        return "insufficient-funds";
    }
}
