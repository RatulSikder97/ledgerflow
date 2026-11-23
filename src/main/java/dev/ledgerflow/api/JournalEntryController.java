package dev.ledgerflow.api;

import dev.ledgerflow.application.Commands;
import dev.ledgerflow.application.IdempotentResult;
import dev.ledgerflow.application.JournalEntryService;
import dev.ledgerflow.application.JournalEntryView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/journal-entries")
@Tag(name = "Journal entries")
public class JournalEntryController {

    private static final String LOCATION = "/api/v1/journal-entries/{id}";

    private final JournalEntryService entries;

    public JournalEntryController(JournalEntryService entries) {
        this.entries = entries;
    }

    @PostMapping
    @Operation(summary = "Post a balanced multi-leg entry",
            description = "Debits must equal credits per currency, so FX legs are expressed as two balanced "
                    + "currency groups through position accounts. Requires an Idempotency-Key header.")
    public ResponseEntity<JournalEntryView> post(
            @RequestHeader(IdempotencyHeaders.KEY) @Pattern(regexp = IdempotencyHeaders.KEY_PATTERN) String key,
            @AuthenticationPrincipal Jwt principal,
            @Valid @RequestBody Requests.PostEntry request) {
        var result = entries.post(principal.getSubject(), key, request.toCommand());
        return IdempotencyHeaders.created(result, LOCATION, result.body().id());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a journal entry with its postings")
    public JournalEntryView get(@PathVariable UUID id) {
        return entries.get(id);
    }

    @PostMapping("/{id}/reversal")
    @Operation(summary = "Reverse an entry by posting its mirror image",
            description = "Entries are immutable; this is the only way to undo one. Naturally idempotent: an "
                    + "entry can be reversed at most once (409 afterwards).")
    public ResponseEntity<JournalEntryView> reverse(@PathVariable UUID id,
                                                    @Valid @RequestBody(required = false) Requests.Reverse request) {
        String reason = request != null ? request.reason() : null;
        JournalEntryView reversal = entries.reverse(new Commands.Reverse(id, reason));
        return IdempotencyHeaders.created(new IdempotentResult<>(reversal, false), LOCATION, reversal.id());
    }
}
