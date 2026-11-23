package dev.ledgerflow.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, IdempotencyRecord.Key> {

    /**
     * Claims a key. Returns 1 if this transaction now owns it, 0 if it already existed. If another in-flight
     * transaction holds the same key, PostgreSQL blocks here until that transaction commits or rolls back.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into idempotency_keys (client_id, idem_key, operation, request_hash, created_at)
            values (:clientId, :key, :operation, :requestHash, now())
            on conflict (client_id, idem_key) do nothing
            """)
    int tryClaim(String clientId, String key, String operation, String requestHash);

    @Modifying
    @Query(nativeQuery = true, value = """
            update idempotency_keys set response_body = :responseBody
            where client_id = :clientId and idem_key = :key
            """)
    int storeResponse(String clientId, String key, String responseBody);
}
