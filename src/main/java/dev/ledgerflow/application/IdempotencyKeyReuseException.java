package dev.ledgerflow.application;

import dev.ledgerflow.domain.LedgerException;

public class IdempotencyKeyReuseException extends LedgerException.RuleViolation {

    public IdempotencyKeyReuseException(String key) {
        super("Idempotency-Key '" + key + "' was already used with a different request payload");
    }

    @Override
    public String problemType() {
        return "idempotency-key-reuse";
    }
}
