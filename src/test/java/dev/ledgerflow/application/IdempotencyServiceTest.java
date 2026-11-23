package dev.ledgerflow.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ledgerflow.persistence.IdempotencyRecordRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyServiceTest {

    private final IdempotencyService service = new IdempotencyService(
            Mockito.mock(IdempotencyRecordRepository.class), new ObjectMapper(), new SimpleMeterRegistry());

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    @Test
    void identicalRequestsHashIdentically() {
        var first = new Commands.Transfer(a, b, 100, "USD", "rent");
        var second = new Commands.Transfer(a, b, 100, "USD", "rent");
        assertThat(service.hash("transfer", first)).isEqualTo(service.hash("transfer", second)).hasSize(64);
    }

    @Test
    void anyFieldChangeChangesTheHash() {
        var base = new Commands.Transfer(a, b, 100, "USD", "rent");
        assertThat(service.hash("transfer", base))
                .isNotEqualTo(service.hash("transfer", new Commands.Transfer(a, b, 101, "USD", "rent")))
                .isNotEqualTo(service.hash("transfer", new Commands.Transfer(b, a, 100, "USD", "rent")))
                .isNotEqualTo(service.hash("transfer", new Commands.Transfer(a, b, 100, "USD", "Rent")));
    }

    @Test
    void theOperationIsPartOfTheHash() {
        var command = new Commands.Transfer(a, b, 100, "USD", null);
        assertThat(service.hash("transfer", command)).isNotEqualTo(service.hash("journal-entry", command));
    }
}
