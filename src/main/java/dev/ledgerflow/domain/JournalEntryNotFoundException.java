package dev.ledgerflow.domain;

import java.util.UUID;

public class JournalEntryNotFoundException extends LedgerException.NotFound {

    public JournalEntryNotFoundException(UUID id) {
        super("journal entry " + id + " does not exist");
    }

    @Override
    public String problemType() {
        return "journal-entry-not-found";
    }
}
