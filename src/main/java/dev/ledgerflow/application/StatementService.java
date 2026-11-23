package dev.ledgerflow.application;

import dev.ledgerflow.domain.AccountNotFoundException;
import dev.ledgerflow.persistence.AccountRepository;
import dev.ledgerflow.persistence.StatementLineRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Queries the eventually consistent statement projection (newest line first).
 */
@Service
public class StatementService {

    private final AccountRepository accounts;
    private final StatementLineRepository lines;

    public StatementService(AccountRepository accounts, StatementLineRepository lines) {
        this.accounts = accounts;
        this.lines = lines;
    }

    @Transactional(readOnly = true)
    public Page<StatementLineView> statement(UUID accountId, int page, int size) {
        if (!accounts.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        return lines.findByAccountIdOrderByPostingIdDesc(accountId, PageRequest.of(page, size))
                .map(StatementLineView::of);
    }
}
