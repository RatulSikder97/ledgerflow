package dev.ledgerflow.application;

import dev.ledgerflow.domain.Account;
import dev.ledgerflow.domain.AccountType;

import java.time.Instant;
import java.util.UUID;

public record AccountView(
        UUID id,
        String code,
        String name,
        AccountType type,
        MoneyView balance,
        boolean allowNegative,
        long version,
        Instant createdAt) {

    public static AccountView of(Account account) {
        return new AccountView(account.getId(), account.getCode(), account.getName(), account.getType(),
                MoneyView.of(account.balance()), account.isAllowNegative(), account.getVersion(),
                account.getCreatedAt());
    }
}
