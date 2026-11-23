package dev.ledgerflow.support;

import dev.ledgerflow.application.AccountService;
import dev.ledgerflow.application.AccountView;
import dev.ledgerflow.application.Commands;
import dev.ledgerflow.application.TransferService;
import dev.ledgerflow.domain.AccountType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

/**
 * Shared Spring context for all integration tests (one set of containers per test JVM).
 */
@SpringBootTest(properties = {
        "ledgerflow.security.dev-jwt-secret=integration-test-secret-0123456789abcdef",
        "ledgerflow.outbox.poll-interval=50ms"
})
@AutoConfigureMockMvc
@AutoConfigureObservability(tracing = false)
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected AccountService accountService;

    @Autowired
    protected TransferService transferService;

    @Autowired
    protected JdbcTemplate jdbc;

    protected AccountView openAccount(AccountType type, String currency, boolean allowNegative) {
        String code = type.name().toLowerCase() + "-" + UUID.randomUUID();
        return accountService.open(new Commands.OpenAccount(code, "Test " + code, type, currency, allowNegative));
    }

    /**
     * Opens a customer wallet (a LIABILITY: the ledger owes the customer) funded from a settlement asset.
     */
    protected AccountView fundedWallet(String currency, long amountMinor) {
        AccountView settlement = openAccount(AccountType.ASSET, currency, false);
        AccountView wallet = openAccount(AccountType.LIABILITY, currency, false);
        if (amountMinor > 0) {
            transferService.transfer("test-setup", "fund-" + UUID.randomUUID(),
                    new Commands.Transfer(settlement.id(), wallet.id(), amountMinor, currency, "initial funding"));
        }
        return accountService.get(wallet.id());
    }

    protected long balanceOf(UUID accountId) {
        return accountService.get(accountId).balance().amountMinor();
    }
}
