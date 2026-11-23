package dev.ledgerflow.api;

import dev.ledgerflow.application.AccountService;
import dev.ledgerflow.application.AccountView;
import dev.ledgerflow.application.StatementLineView;
import dev.ledgerflow.application.StatementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts")
public class AccountController {

    private final AccountService accounts;
    private final StatementService statements;

    public AccountController(AccountService accounts, StatementService statements) {
        this.accounts = accounts;
        this.statements = statements;
    }

    @PostMapping
    @Operation(summary = "Open an account")
    public ResponseEntity<AccountView> open(@Valid @RequestBody Requests.OpenAccount request) {
        AccountView account = accounts.open(request.toCommand());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(account.id()).toUri()).body(account);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an account with its current balance (strongly consistent)")
    public AccountView get(@PathVariable UUID id) {
        return accounts.get(id);
    }

    @GetMapping("/{id}/statement")
    @Operation(summary = "Account statement, newest first (eventually consistent projection)")
    public PagedModel<StatementLineView> statement(@PathVariable UUID id,
                                                   @RequestParam(defaultValue = "0") @Min(0) int page,
                                                   @RequestParam(defaultValue = "50") @Min(1) @Max(500) int size) {
        return new PagedModel<>(statements.statement(id, page, size));
    }
}
