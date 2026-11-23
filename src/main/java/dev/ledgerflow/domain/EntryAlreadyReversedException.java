package dev.ledgerflow.domain;

import java.util.UUID;

public class EntryAlreadyReversedException extends LedgerException.Conflict {

    public EntryAlreadyReversedException(UUID entryId) {
        super("journal entry " + entryId + " has already been reversed");
    }

    @Override
    public String problemType() {
        return "entry-already-reversed";
    }
}
