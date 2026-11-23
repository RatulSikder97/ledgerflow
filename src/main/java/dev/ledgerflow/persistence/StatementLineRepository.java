package dev.ledgerflow.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface StatementLineRepository extends JpaRepository<StatementLine, Long> {

    Page<StatementLine> findByAccountIdOrderByPostingIdDesc(UUID accountId, Pageable pageable);

    long countByAccountId(UUID accountId);

    @Modifying
    @Query(nativeQuery = true, value = """
            insert into statement_lines (posting_id, account_id, entry_id, entry_type, direction, amount_minor,
                                         currency, balance_after, description, occurred_at)
            values (:postingId, :accountId, :entryId, :entryType, :direction, :amountMinor,
                    :currency, :balanceAfter, :description, :occurredAt)
            on conflict (posting_id) do nothing
            """)
    int insertIfAbsent(long postingId, UUID accountId, UUID entryId, String entryType, String direction,
                       long amountMinor, String currency, long balanceAfter, String description, Instant occurredAt);
}
