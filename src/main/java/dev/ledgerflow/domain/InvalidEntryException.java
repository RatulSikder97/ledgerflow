package dev.ledgerflow.domain;

public class InvalidEntryException extends LedgerException.RuleViolation {

    public InvalidEntryException(String message) {
        super(message);
    }

    @Override
    public String problemType() {
        return "invalid-entry";
    }
}
