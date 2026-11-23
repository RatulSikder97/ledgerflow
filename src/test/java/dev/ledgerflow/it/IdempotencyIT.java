package dev.ledgerflow.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ledgerflow.application.AccountView;
import dev.ledgerflow.domain.AccountType;
import dev.ledgerflow.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IdempotencyIT extends AbstractIntegrationTest {

    @Autowired
    ObjectMapper objectMapper;

    private MockHttpServletRequestBuilder transfer(String key, String subject, UUID from, UUID to, long amount) {
        return post("/api/v1/transfers")
                .with(jwt().jwt(j -> j.subject(subject).claim("scope", "ledger.write")))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sourceAccountId":"%s","destinationAccountId":"%s","amountMinor":%d,"currency":"USD"}
                        """.formatted(from, to, amount));
    }

    @Test
    void retriedRequestIsExecutedOnceAndReplaysTheOriginalResponse() throws Exception {
        AccountView alice = fundedWallet("USD", 10_000);
        AccountView bob = openAccount(AccountType.LIABILITY, "USD", false);
        String key = "retry-" + UUID.randomUUID();

        MvcResult first = mvc.perform(transfer(key, "client-a", alice.id(), bob.id(), 2_500))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andReturn();
        MvcResult second = mvc.perform(transfer(key, "client-a", alice.id(), bob.id(), 2_500))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andReturn();

        JsonNode original = objectMapper.readTree(first.getResponse().getContentAsString());
        JsonNode replay = objectMapper.readTree(second.getResponse().getContentAsString());
        assertThat(replay).isEqualTo(original);
        assertThat(balanceOf(alice.id())).isEqualTo(7_500);
        assertThat(balanceOf(bob.id())).isEqualTo(2_500);
    }

    @Test
    void reusingAKeyWithADifferentPayloadIsRejected() throws Exception {
        AccountView alice = fundedWallet("USD", 10_000);
        AccountView bob = openAccount(AccountType.LIABILITY, "USD", false);
        String key = "reuse-" + UUID.randomUUID();

        mvc.perform(transfer(key, "client-a", alice.id(), bob.id(), 100)).andExpect(status().isCreated());
        mvc.perform(transfer(key, "client-a", alice.id(), bob.id(), 999))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:ledgerflow:problem:idempotency-key-reuse"));

        assertThat(balanceOf(bob.id())).isEqualTo(100);
    }

    @Test
    void keysAreScopedPerClient() throws Exception {
        AccountView alice = fundedWallet("USD", 10_000);
        AccountView bob = openAccount(AccountType.LIABILITY, "USD", false);
        String key = "shared-" + UUID.randomUUID();

        mvc.perform(transfer(key, "client-a", alice.id(), bob.id(), 100))
                .andExpect(header().string("Idempotent-Replayed", "false"));
        mvc.perform(transfer(key, "client-b", alice.id(), bob.id(), 100))
                .andExpect(header().string("Idempotent-Replayed", "false"));

        assertThat(balanceOf(bob.id())).isEqualTo(200);
    }

    @Test
    void failedRequestsDoNotBurnTheKey() throws Exception {
        AccountView alice = fundedWallet("USD", 100);
        AccountView bob = openAccount(AccountType.LIABILITY, "USD", false);
        String key = "fail-" + UUID.randomUUID();

        mvc.perform(transfer(key, "client-a", alice.id(), bob.id(), 500))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:ledgerflow:problem:insufficient-funds"));

        transferService.transfer("top-up", UUID.randomUUID().toString(), new dev.ledgerflow.application.Commands
                .Transfer(fundedWallet("USD", 1_000).id(), alice.id(), 1_000, "USD", null));

        mvc.perform(transfer(key, "client-a", alice.id(), bob.id(), 500))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"));
    }

    @Test
    void concurrentDuplicatesExecuteExactlyOnce() throws Exception {
        AccountView alice = fundedWallet("USD", 10_000);
        AccountView bob = openAccount(AccountType.LIABILITY, "USD", false);
        String key = "race-" + UUID.randomUUID();

        List<Callable<MvcResult>> calls = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            calls.add(() -> mvc.perform(transfer(key, "client-a", alice.id(), bob.id(), 1_000)).andReturn());
        }
        List<MvcResult> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Future<MvcResult> f : pool.invokeAll(calls)) {
                results.add(f.get());
            }
        }

        assertThat(results).allSatisfy(r -> assertThat(r.getResponse().getStatus()).isEqualTo(201));
        assertThat(results).filteredOn(r -> "false".equals(r.getResponse().getHeader("Idempotent-Replayed")))
                .hasSize(1);
        assertThat(balanceOf(bob.id())).isEqualTo(1_000);
    }

    @Test
    void theHeaderIsRequired() throws Exception {
        mvc.perform(post("/api/v1/transfers")
                        .with(jwt().jwt(j -> j.claim("scope", "ledger.write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceAccountId":"%s","destinationAccountId":"%s","amountMinor":1,"currency":"USD"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
