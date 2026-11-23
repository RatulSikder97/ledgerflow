package dev.ledgerflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/**
 * A ledger account. The balance is a denormalised running total of its postings, expressed on the account's
 * normal side (a debit increases an ASSET, a credit increases a LIABILITY). {@link #version} gives optimistic
 * concurrency control on top of the row lock taken when posting.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccountType type;

    @Column(nullable = false, length = 3)
    private Currency currency;

    @Column(name = "balance_minor", nullable = false)
    private long balanceMinor;

    @Column(name = "allow_negative", nullable = false)
    private boolean allowNegative;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {
    }

    private Account(UUID id, String code, String name, AccountType type, Currency currency, boolean allowNegative,
                    Instant createdAt) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.type = type;
        this.currency = currency;
        this.allowNegative = allowNegative;
        this.createdAt = createdAt;
    }

    public static Account open(String code, String name, AccountType type, Currency currency, boolean allowNegative,
                               Instant now) {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(currency, "currency");
        return new Account(Ids.newId(), code, name, type, currency, allowNegative, now.truncatedTo(ChronoUnit.MICROS));
    }

    /**
     * Applies one posting and returns the resulting balance. Limits are checked separately via
     * {@link #assertWithinLimits()} once every leg of an entry has been applied, so a multi-leg entry that
     * touches the same account twice is judged on its net effect.
     */
    long apply(Direction direction, long amountMinor, Currency postingCurrency) {
        if (!currency.equals(postingCurrency)) {
            throw new CurrencyMismatchException(currency, postingCurrency);
        }
        balanceMinor = direction == type.normalSide()
                ? Math.addExact(balanceMinor, amountMinor)
                : Math.subtractExact(balanceMinor, amountMinor);
        return balanceMinor;
    }

    void assertWithinLimits() {
        if (!allowNegative && balanceMinor < 0) {
            throw new InsufficientFundsException(id, balance());
        }
    }

    public Money balance() {
        return new Money(balanceMinor, currency);
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public AccountType getType() {
        return type;
    }

    public Currency getCurrency() {
        return currency;
    }

    public boolean isAllowNegative() {
        return allowNegative;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
