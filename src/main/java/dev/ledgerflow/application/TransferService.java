package dev.ledgerflow.application;

import dev.ledgerflow.domain.EntryType;
import dev.ledgerflow.domain.InvalidEntryException;
import dev.ledgerflow.domain.Money;
import dev.ledgerflow.domain.PostingLine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Moves money between two accounts of the same currency: debit the source, credit the destination.
 */
@Service
public class TransferService {

    static final String OPERATION = "transfer";

    private final IdempotencyService idempotency;
    private final LedgerPoster poster;

    public TransferService(IdempotencyService idempotency, LedgerPoster poster) {
        this.idempotency = idempotency;
        this.poster = poster;
    }

    @Transactional
    public IdempotentResult<JournalEntryView> transfer(String clientId, String idempotencyKey,
                                                       Commands.Transfer command) {
        return idempotency.execute(clientId, idempotencyKey, OPERATION, command, JournalEntryView.class,
                () -> execute(command));
    }

    private JournalEntryView execute(Commands.Transfer command) {
        if (command.sourceAccountId().equals(command.destinationAccountId())) {
            throw new InvalidEntryException("source and destination account must differ");
        }
        Money amount = Money.of(command.amountMinor(), command.currency());
        List<PostingLine> lines = List.of(
                PostingLine.debit(command.sourceAccountId(), amount),
                PostingLine.credit(command.destinationAccountId(), amount));
        return poster.post(EntryType.TRANSFER, command.description(), lines);
    }
}
