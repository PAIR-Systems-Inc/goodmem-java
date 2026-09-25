package ai.pairsys.goodmem.client.errors;

/**
 * HTTP 409 Conflict — the request collides with existing server state.
 * Most often raised on idempotent {@code create} retries: when a
 * caller supplies a stable {@code memoryId} / {@code spaceId} /
 * {@code apiKeyId} that already exists, the first request creates the
 * resource and subsequent retries surface as 409 ALREADY_EXISTS rather
 * than producing duplicates. Treat as a successful idempotent retry.
 */
public class ConflictException extends ApiException {
    public ConflictException(String message, int statusCode, String body) {
        super(message, statusCode, body);
    }
}
