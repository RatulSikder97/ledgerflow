package dev.ledgerflow.api;

import dev.ledgerflow.application.IdempotentResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

final class IdempotencyHeaders {

    static final String KEY = "Idempotency-Key";
    static final String REPLAYED = "Idempotent-Replayed";
    static final String KEY_PATTERN = "[A-Za-z0-9_.:-]{8,255}";

    private IdempotencyHeaders() {
    }

    static <T> ResponseEntity<T> created(IdempotentResult<T> result, String locationTemplate, UUID id) {
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath().path(locationTemplate)
                .buildAndExpand(id).toUri();
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(location)
                .header(REPLAYED, Boolean.toString(result.replayed()))
                .body(result.body());
    }
}
