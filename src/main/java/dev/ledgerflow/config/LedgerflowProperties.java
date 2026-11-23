package dev.ledgerflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("ledgerflow")
public record LedgerflowProperties(
        @DefaultValue("ledger.journal-entries") String topic,
        @DefaultValue("3") int topicPartitions,
        @DefaultValue Outbox outbox,
        @DefaultValue Security security) {

    public record Outbox(
            @DefaultValue("true") boolean relayEnabled,
            @DefaultValue("100") int batchSize,
            @DefaultValue("250ms") Duration pollInterval,
            @DefaultValue("10s") Duration sendTimeout) {
    }

    /**
     * @param devJwtSecret HMAC secret for locally minted tokens. Only for local development and demos; leave
     *                     unset in real deployments and configure {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}.
     */
    public record Security(String devJwtSecret) {
    }
}
