package dev.ledgerflow.domain;

import java.util.UUID;

public class AccountNotFoundException extends LedgerException.NotFound {

    public AccountNotFoundException(UUID id) {
        super("account " + id + " does not exist");
    }

    @Override
    public String problemType() {
        return "account-not-found";
    }
}
