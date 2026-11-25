package ai.pairsys.goodmem.client.errors;

/**
 * HTTP 5xx — the server failed to handle a well-formed request
 * (database error, embedder timeout, retrieval pipeline failure, etc.).
 * Treat as a transient failure: retrying with backoff is appropriate
 * for idempotent operations. The message and {@link #getBody()} carry
 * the server's diagnostic so logs can surface a meaningful cause.
 */
public class InternalServerException extends ApiException {
    private final Long retryAfterSeconds;

    /** Parsed Retry-After on HTTP 503, or null. */
    public Long getRetryAfterSeconds() { return retryAfterSeconds; }

    public InternalServerException(String message, int statusCode, String body, Long retryAfterSeconds) {
        super(message, statusCode, body);
        this.retryAfterSeconds = retryAfterSeconds;
    }
    public InternalServerException(String message, int statusCode, String body) {
        this(message, statusCode, body, null);
    }
}
