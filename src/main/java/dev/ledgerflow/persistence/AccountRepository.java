package dev.ledgerflow.persistence;

import dev.ledgerflow.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    /**
     * Row-locks the accounts in primary-key order. A global lock order means two transfers touching the same
     * pair of accounts in opposite directions queue up instead of deadlocking.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id in :ids order by a.id")
    List<Account> findAllByIdForUpdate(Collection<UUID> ids);

    boolean existsByCode(String code);
}
