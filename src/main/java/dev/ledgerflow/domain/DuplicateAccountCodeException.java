package dev.ledgerflow.domain;

public class DuplicateAccountCodeException extends LedgerException.Conflict {

    public DuplicateAccountCodeException(String code) {
        super("an account with code '" + code + "' already exists");
    }

    @Override
    public String problemType() {
        return "duplicate-account-code";
    }
}
