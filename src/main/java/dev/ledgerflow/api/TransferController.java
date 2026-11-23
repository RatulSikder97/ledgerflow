package dev.ledgerflow.api;

import dev.ledgerflow.application.JournalEntryView;
import dev.ledgerflow.application.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transfers")
public class TransferController {

    private final TransferService transfers;

    public TransferController(TransferService transfers) {
        this.transfers = transfers;
    }

    @PostMapping
    @Operation(summary = "Transfer money between two accounts of the same currency",
            description = "Requires an Idempotency-Key header. Retrying with the same key and body returns the "
                    + "original response with Idempotent-Replayed: true; the same key with a different body is "
                    + "rejected with 422.")
    public ResponseEntity<JournalEntryView> transfer(
            @RequestHeader(IdempotencyHeaders.KEY) @Pattern(regexp = IdempotencyHeaders.KEY_PATTERN) String key,
            @AuthenticationPrincipal Jwt principal,
            @Valid @RequestBody Requests.Transfer request) {
        var result = transfers.transfer(principal.getSubject(), key, request.toCommand());
        return IdempotencyHeaders.created(result, "/api/v1/journal-entries/{id}", result.body().id());
    }
}
