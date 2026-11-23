package dev.ledgerflow.domain;

/**
 * Base type for expected ledger failures. {@link #problemType()} is a stable, machine-readable slug.
 */
public abstract class LedgerException extends RuntimeException {

    protected LedgerException(String message) {
        super(message);
    }

    public abstract String problemType();

    /** Requested resource does not exist. */
    public abstract static class NotFound extends LedgerException {
        protected NotFound(String message) {
            super(message);
        }
    }

    /** Request conflicts with the current state of a resource. */
    public abstract static class Conflict extends LedgerException {
        protected Conflict(String message) {
            super(message);
        }
    }

    /** Request is well-formed but violates a ledger rule. */
    public abstract static class RuleViolation extends LedgerException {
        protected RuleViolation(String message) {
            super(message);
        }
    }
}
