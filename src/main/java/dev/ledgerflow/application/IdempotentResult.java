package dev.ledgerflow.application;

/**
 * @param replayed true when {@code body} is the stored response of an earlier request with the same key
 */
public record IdempotentResult<T>(T body, boolean replayed) {
}
