-- Write model: accounts, immutable journal entries and their postings.

create table accounts (
    id             uuid         primary key,
    code           varchar(64)  not null unique,
    name           varchar(200) not null,
    type           varchar(16)  not null check (type in ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE')),
    currency       varchar(3)   not null check (currency ~ '^[A-Z]{3}$'),
    balance_minor  bigint       not null default 0,
    allow_negative boolean      not null default false,
    version        bigint       not null default 0,
    created_at     timestamptz  not null default now(),
    constraint accounts_balance_non_negative check (allow_negative or balance_minor >= 0)
);

create table journal_entries (
    id                uuid         primary key,
    type              varchar(16)  not null check (type in ('TRANSFER', 'MANUAL', 'REVERSAL')),
    description       varchar(500),
    reverses_entry_id uuid         unique references journal_entries (id),
    created_at        timestamptz  not null,
    constraint journal_entries_reversal_link check ((type = 'REVERSAL') = (reverses_entry_id is not null))
);

create table postings (
    id            bigint generated always as identity primary key,
    entry_id      uuid        not null references journal_entries (id),
    account_id    uuid        not null references accounts (id),
    direction     varchar(6)  not null check (direction in ('DEBIT', 'CREDIT')),
    amount_minor  bigint      not null check (amount_minor > 0),
    currency      varchar(3)  not null,
    balance_after bigint      not null
);

create index postings_entry_idx on postings (entry_id);
create index postings_account_idx on postings (account_id, id);

-- Invariant 1: every journal entry balances per currency (sum of debits == sum of credits)
-- and has at least two postings. Checked as DEFERRED constraint triggers, i.e. at COMMIT,
-- so the application can insert the header and its postings in any order within one transaction.

create function ledger_assert_entry_balanced(p_entry_id uuid) returns void
    language plpgsql as
$$
declare
    v_postings   int;
    v_unbalanced text;
begin
    select count(*) into v_postings from postings where entry_id = p_entry_id;
    if v_postings < 2 then
        raise exception 'journal entry % must have at least two postings (has %)', p_entry_id, v_postings
            using errcode = 'check_violation';
    end if;

    select string_agg(currency || ':' || net, ', ')
    into v_unbalanced
    from (select currency,
                 sum(case direction when 'DEBIT' then amount_minor else -amount_minor end) as net
          from postings
          where entry_id = p_entry_id
          group by currency) per_currency
    where net <> 0;

    if v_unbalanced is not null then
        raise exception 'journal entry % is not balanced (%)', p_entry_id, v_unbalanced
            using errcode = 'check_violation';
    end if;
end;
$$;

create function ledger_check_posting_entry() returns trigger
    language plpgsql as
$$
begin
    perform ledger_assert_entry_balanced(new.entry_id);
    return null;
end;
$$;

create function ledger_check_entry() returns trigger
    language plpgsql as
$$
begin
    perform ledger_assert_entry_balanced(new.id);
    return null;
end;
$$;

create constraint trigger postings_balanced
    after insert on postings
    deferrable initially deferred
    for each row execute function ledger_check_posting_entry();

create constraint trigger journal_entries_balanced
    after insert on journal_entries
    deferrable initially deferred
    for each row execute function ledger_check_entry();

-- Invariant 2: posted history is append-only. Corrections are made with reversal entries.

create function ledger_reject_mutation() returns trigger
    language plpgsql as
$$
begin
    raise exception '% is append-only: % is not allowed', tg_table_name, tg_op
        using errcode = 'restrict_violation';
end;
$$;

create trigger journal_entries_immutable
    before update or delete on journal_entries
    for each row execute function ledger_reject_mutation();

create trigger postings_immutable
    before update or delete on postings
    for each row execute function ledger_reject_mutation();

-- Idempotency: one stored outcome per (client, key).

create table idempotency_keys (
    client_id     varchar(255) not null,
    idem_key      varchar(255) not null,
    operation     varchar(64)  not null,
    request_hash  varchar(64)  not null,
    response_body text,
    created_at    timestamptz  not null default now(),
    primary key (client_id, idem_key)
);

-- Transactional outbox, drained by the relay with FOR UPDATE SKIP LOCKED.

create table outbox_events (
    id             uuid         primary key,
    aggregate_type varchar(64)  not null,
    aggregate_id   uuid         not null,
    event_type     varchar(128) not null,
    payload        text         not null,
    created_at     timestamptz  not null,
    published_at   timestamptz,
    attempts       int          not null default 0
);

create index outbox_events_pending_idx on outbox_events (created_at) where published_at is null;

-- Read model: per-account statements, built by the Kafka consumer.

create table processed_events (
    consumer     varchar(128) not null,
    event_id     uuid         not null,
    processed_at timestamptz  not null default now(),
    primary key (consumer, event_id)
);

create table statement_lines (
    posting_id    bigint       primary key,
    account_id    uuid         not null,
    entry_id      uuid         not null,
    entry_type    varchar(16)  not null,
    direction     varchar(6)   not null,
    amount_minor  bigint       not null,
    currency      varchar(3)   not null,
    balance_after bigint       not null,
    description   varchar(500),
    occurred_at   timestamptz  not null
);

create index statement_lines_account_idx on statement_lines (account_id, posting_id);
