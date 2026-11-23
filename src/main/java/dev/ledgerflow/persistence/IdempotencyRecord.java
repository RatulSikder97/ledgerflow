package dev.ledgerflow.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "idempotency_keys")
@IdClass(IdempotencyRecord.Key.class)
public class IdempotencyRecord {

    @Id
    @Column(name = "client_id")
    private String clientId;

    @Id
    @Column(name = "idem_key")
    private String idemKey;

    @Column(nullable = false, length = 64)
    private String operation;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
    }

    public String getOperation() {
        return operation;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public record Key(String clientId, String idemKey) implements Serializable {
    }
}
