package dev.ledgerflow.domain;

public class UnbalancedEntryException extends LedgerException.RuleViolation {

    public UnbalancedEntryException(String detail) {
        super("journal entry is not balanced: " + detail);
    }

    @Override
    public String problemType() {
        return "unbalanced-entry";
    }
}
