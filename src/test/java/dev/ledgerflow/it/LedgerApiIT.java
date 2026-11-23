package dev.ledgerflow.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ledgerflow.application.AccountView;
import dev.ledgerflow.domain.AccountType;
import dev.ledgerflow.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LedgerApiIT extends AbstractIntegrationTest {

    @Autowired
    ObjectMapper objectMapper;

    private static RequestPostProcessor writer() {
        return jwt().jwt(j -> j.subject("api-test").claim("scope", "ledger.read ledger.write"));
    }

    private static RequestPostProcessor reader() {
        return jwt().jwt(j -> j.subject("api-test").claim("scope", "ledger.read"));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.with(writer()).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("Idempotency-Key", UUID.randomUUID().toString());
    }

    @Test
    void opensAndReadsAnAccount() throws Exception {
        String code = "acct-" + UUID.randomUUID();
        String body = mvc.perform(json(post("/api/v1/accounts"), """
                        {"code":"%s","name":"Operating","type":"ASSET","currency":"JPY"}
                        """.formatted(code)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("http://localhost/api/v1/accounts/")))
                .andExpect(jsonPath("$.balance.amountMinor").value(0))
                .andExpect(jsonPath("$.balance.currency").value("JPY"))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).get("id").asText();

        mvc.perform(get("/api/v1/accounts/{id}", id).with(reader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code));

        mvc.perform(json(post("/api/v1/accounts"), """
                        {"code":"%s","name":"Again","type":"ASSET","currency":"JPY"}
                        """.formatted(code)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ledgerflow:problem:duplicate-account-code"));
    }

    @Test
    void validationErrorsAreProblemDetails() throws Exception {
        mvc.perform(json(post("/api/v1/accounts"), """
                        {"code":"has space","name":"x","type":"ASSET","currency":"usd"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:ledgerflow:problem:validation-failed"))
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void transferRejectsCurrencyMismatchAndInsufficientFunds() throws Exception {
        AccountView usd = fundedWallet("USD", 1_000);
        AccountView eur = openAccount(AccountType.LIABILITY, "EUR", false);
        AccountView usd2 = openAccount(AccountType.LIABILITY, "USD", false);

        mvc.perform(json(post("/api/v1/transfers"), """
                        {"sourceAccountId":"%s","destinationAccountId":"%s","amountMinor":100,"currency":"USD"}
                        """.formatted(usd.id(), eur.id())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:ledgerflow:problem:currency-mismatch"));

        mvc.perform(json(post("/api/v1/transfers"), """
                        {"sourceAccountId":"%s","destinationAccountId":"%s","amountMinor":1001,"currency":"USD"}
                        """.formatted(usd.id(), usd2.id())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:ledgerflow:problem:insufficient-funds"))
                .andExpect(jsonPath("$.retryable").value(false));

        mvc.perform(json(post("/api/v1/transfers"), """
                        {"sourceAccountId":"%s","destinationAccountId":"%s","amountMinor":1,"currency":"USD"}
                        """.formatted(usd.id(), UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:ledgerflow:problem:account-not-found"));

        assertThat(balanceOf(usd.id())).isEqualTo(1_000);
    }

    @Test
    void postsAMultiCurrencyEntryBalancedPerCurrency() throws Exception {
        // Customer converts 100.00 USD into 92.00 EUR through the house FX position accounts.
        AccountView customerUsd = fundedWallet("USD", 10_000);
        AccountView customerEur = openAccount(AccountType.LIABILITY, "EUR", false);
        AccountView fxUsd = openAccount(AccountType.EQUITY, "USD", true);
        AccountView fxEur = openAccount(AccountType.EQUITY, "EUR", true);

        mvc.perform(json(post("/api/v1/journal-entries"), """
                        {"description":"FX USD->EUR @0.92","postings":[
                          {"accountId":"%s","direction":"DEBIT","amountMinor":10000,"currency":"USD"},
                          {"accountId":"%s","direction":"CREDIT","amountMinor":10000,"currency":"USD"},
                          {"accountId":"%s","direction":"DEBIT","amountMinor":9200,"currency":"EUR"},
                          {"accountId":"%s","direction":"CREDIT","amountMinor":9200,"currency":"EUR"}]}
                        """.formatted(customerUsd.id(), fxUsd.id(), fxEur.id(), customerEur.id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("MANUAL"))
                .andExpect(jsonPath("$.postings.length()").value(4));

        assertThat(balanceOf(customerUsd.id())).isZero();
        assertThat(balanceOf(customerEur.id())).isEqualTo(9_200);
        assertThat(balanceOf(fxEur.id())).isEqualTo(-9_200);

        mvc.perform(json(post("/api/v1/journal-entries"), """
                        {"postings":[
                          {"accountId":"%s","direction":"DEBIT","amountMinor":100,"currency":"EUR"},
                          {"accountId":"%s","direction":"CREDIT","amountMinor":99,"currency":"EUR"}]}
                        """.formatted(fxEur.id(), customerEur.id())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:ledgerflow:problem:unbalanced-entry"));
    }

    @Test
    void reversesAnEntryExactlyOnce() throws Exception {
        AccountView alice = fundedWallet("USD", 1_000);
        AccountView bob = openAccount(AccountType.LIABILITY, "USD", false);

        String created = mvc.perform(json(post("/api/v1/transfers"), """
                        {"sourceAccountId":"%s","destinationAccountId":"%s","amountMinor":400,"currency":"USD"}
                        """.formatted(alice.id(), bob.id())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode entry = objectMapper.readTree(created);
        String entryId = entry.get("id").asText();

        mvc.perform(get("/api/v1/journal-entries/{id}", entryId).with(reader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postings[0].direction").value("DEBIT"))
                .andExpect(jsonPath("$.postings[0].balanceAfter.amountMinor").value(600));

        mvc.perform(post("/api/v1/journal-entries/{id}/reversal", entryId).with(writer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"sent to wrong account\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("REVERSAL"))
                .andExpect(jsonPath("$.reversesEntryId").value(entryId));

        assertThat(balanceOf(alice.id())).isEqualTo(1_000);
        assertThat(balanceOf(bob.id())).isZero();

        mvc.perform(post("/api/v1/journal-entries/{id}/reversal", entryId).with(writer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ledgerflow:problem:entry-already-reversed"));
    }

    @Test
    void enforcesAuthenticationAndScopes() throws Exception {
        mvc.perform(get("/api/v1/accounts/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/accounts").with(reader()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"x\",\"name\":\"x\",\"type\":\"ASSET\",\"currency\":\"USD\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("ledgerflow_entries_posted_total")));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/transfers']").exists());
    }
}
