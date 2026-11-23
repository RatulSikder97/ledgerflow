package dev.ledgerflow.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ledgerflow.application.AccountView;
import dev.ledgerflow.application.Commands;
import dev.ledgerflow.application.JournalEntryView;
import dev.ledgerflow.application.StatementProjector;
import dev.ledgerflow.application.StatementService;
import dev.ledgerflow.domain.AccountType;
import dev.ledgerflow.support.AbstractIntegrationTest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class OutboxProjectionIT extends AbstractIntegrationTest {

    @Autowired
    StatementService statements;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    KafkaAdmin kafkaAdmin;

    @Autowired
    MeterRegistry meterRegistry;

    @Test
    void committedTransferFlowsThroughOutboxAndKafkaIntoTheStatement() {
        AccountView alice = fundedWallet("EUR", 5_000);
        AccountView bob = openAccount(AccountType.LIABILITY, "EUR", false);

        JournalEntryView entry = transferService.transfer("client", UUID.randomUUID().toString(),
                new Commands.Transfer(alice.id(), bob.id(), 1_250, "EUR", "dinner")).body();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var bobLines = statements.statement(bob.id(), 0, 10).getContent();
            assertThat(bobLines).singleElement().satisfies(line -> {
                assertThat(line.entryId()).isEqualTo(entry.id());
                assertThat(line.direction()).isEqualTo("CREDIT");
                assertThat(line.amount().amountMinor()).isEqualTo(1_250);
                assertThat(line.balanceAfter().amountMinor()).isEqualTo(1_250);
                assertThat(line.description()).isEqualTo("dinner");
            });
            // Alice: funding credit then this debit, newest first.
            assertThat(statements.statement(alice.id(), 0, 10).getContent())
                    .extracting(l -> l.balanceAfter().amountMinor())
                    .containsExactly(3_750L, 5_000L);
        });

        Integer unpublished = jdbc.queryForObject(
                "select count(*) from outbox_events where aggregate_id = ? and published_at is null",
                Integer.class, entry.id());
        assertThat(unpublished).isZero();
    }

    @Test
    void redeliveredEventsHaveNoAdditionalEffect() throws Exception {
        AccountView alice = fundedWallet("GBP", 900);
        AccountView bob = openAccount(AccountType.LIABILITY, "GBP", false);
        JournalEntryView entry = transferService.transfer("client", UUID.randomUUID().toString(),
                new Commands.Transfer(alice.id(), bob.id(), 300, "GBP", null)).body();

        await().atMost(Duration.ofSeconds(30))
                .until(() -> statements.statement(bob.id(), 0, 10).getTotalElements() == 1);

        double duplicatesBefore = duplicateCount();

        // Simulate at-least-once delivery: publish the very same outbox payload again.
        String payload = jdbc.queryForObject("select payload from outbox_events where aggregate_id = ?",
                String.class, entry.id());
        RecordMetadata duplicate = kafka.send("ledger.journal-entries", entry.id().toString(), payload).get()
                .getRecordMetadata();

        // Wait until the projector has committed past the duplicate, i.e. has seen and handled it.
        TopicPartition partition = new TopicPartition(duplicate.topic(), duplicate.partition());
        try (Admin admin = Admin.create(kafkaAdmin.getConfigurationProperties())) {
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                OffsetAndMetadata committed = admin.listConsumerGroupOffsets(StatementProjector.CONSUMER)
                        .partitionsToOffsetAndMetadata().get().get(partition);
                return committed != null && committed.offset() > duplicate.offset();
            });
        }

        assertThat(duplicateCount()).isEqualTo(duplicatesBefore + 1);
        assertThat(statements.statement(bob.id(), 0, 10).getTotalElements()).isEqualTo(1);
        Integer markers = jdbc.queryForObject("select count(*) from processed_events where event_id = ?::uuid",
                Integer.class, objectMapper.readTree(payload).get("eventId").asText());
        assertThat(markers).isEqualTo(1);
    }

    private double duplicateCount() {
        Counter counter = meterRegistry.find("ledgerflow.projection.events").tag("result", "duplicate").counter();
        return counter == null ? 0 : counter.count();
    }
}
