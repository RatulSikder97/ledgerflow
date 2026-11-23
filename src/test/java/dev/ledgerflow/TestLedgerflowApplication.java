package dev.ledgerflow;

import dev.ledgerflow.support.TestcontainersConfiguration;
import org.springframework.boot.SpringApplication;

/**
 * Runs the app against throwaway containers: {@code ./mvnw spring-boot:test-run}.
 */
public class TestLedgerflowApplication {

    public static void main(String[] args) {
        SpringApplication.from(LedgerflowApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
