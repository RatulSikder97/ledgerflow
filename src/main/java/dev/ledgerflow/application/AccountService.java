package dev.ledgerflow.application;

import dev.ledgerflow.domain.Account;
import dev.ledgerflow.domain.AccountNotFoundException;
import dev.ledgerflow.domain.DuplicateAccountCodeException;
import dev.ledgerflow.domain.Money;
import dev.ledgerflow.persistence.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final Clock clock;

    public AccountService(AccountRepository accounts, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional
    public AccountView open(Commands.OpenAccount command) {
        if (accounts.existsByCode(command.code())) {
            throw new DuplicateAccountCodeException(command.code());
        }
        Account account = Account.open(command.code(), command.name(), command.type(),
                Money.currency(command.currency()), command.allowNegative(), clock.instant());
        return AccountView.of(accounts.saveAndFlush(account));
    }

    @Transactional(readOnly = true)
    public AccountView get(UUID id) {
        return accounts.findById(id).map(AccountView::of).orElseThrow(() -> new AccountNotFoundException(id));
    }
}
