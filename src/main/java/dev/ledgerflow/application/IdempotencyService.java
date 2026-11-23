package dev.ledgerflow.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ledgerflow.persistence.IdempotencyRecord;
import dev.ledgerflow.persistence.IdempotencyRecordRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Supplier;

/**
 * Runs an operation at most once per (client, Idempotency-Key).
 * <p>
 * The key is claimed, the operation executed and the response stored in the caller's transaction. Either all
 * three commit or none do: a failed operation releases the key, a successful one can never be repeated. A
 * concurrent duplicate blocks on the key's unique index until the first request finishes, then replays its
 * response.
 */
@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final Counter replays;

    public IdempotencyService(IdempotencyRecordRepository repository, ObjectMapper objectMapper,
                              MeterRegistry meterRegistry) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.replays = Counter.builder("ledgerflow.idempotency.replays")
                .description("Requests answered from a stored idempotent response")
                .register(meterRegistry);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public <T> IdempotentResult<T> execute(String clientId, String key, String operation, Object request,
                                           Class<T> responseType, Supplier<T> action) {
        String requestHash = hash(operation, request);
        if (repository.tryClaim(clientId, key, operation, requestHash) == 0) {
            return replay(clientId, key, operation, requestHash, responseType);
        }
        T response = action.get();
        repository.storeResponse(clientId, key, write(response));
        return new IdempotentResult<>(response, false);
    }

    private <T> IdempotentResult<T> replay(String clientId, String key, String operation, String requestHash,
                                           Class<T> responseType) {
        IdempotencyRecord existing = repository.findById(new IdempotencyRecord.Key(clientId, key))
                .orElseThrow(() -> new IllegalStateException("idempotency key vanished after conflict: " + key));
        if (!existing.getOperation().equals(operation) || !existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyReuseException(key);
        }
        replays.increment();
        try {
            return new IdempotentResult<>(objectMapper.readValue(existing.getResponseBody(), responseType), true);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("stored idempotent response is unreadable", e);
        }
    }

    String hash(String operation, Object request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(operation.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(objectMapper.writeValueAsBytes(request));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new IllegalStateException("cannot hash request", e);
        }
    }

    private String write(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialise response", e);
        }
    }
}
