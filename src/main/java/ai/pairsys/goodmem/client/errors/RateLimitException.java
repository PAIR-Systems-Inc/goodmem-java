package ai.pairsys.goodmem.client.errors;

/**
 * HTTP 429 Too Many Requests — the caller (or the upstream provider
 * the server is proxying to, e.g. an LLM/embedder) exceeded its rate
 * limit. Back off and retry.
 *
 * <p>The goodmem server includes a {@code Retry-After} response
 * header in integer-seconds form (RFC 7231 delta-seconds — see
 * server's {@code RateLimitingInterceptor}). When present, the
 * parsed value is exposed via {@link #getRetryAfterSeconds()}; when
 * absent (the common case on 429s that don't carry a rate-limit
 * verdict), {@code getRetryAfterSeconds()} returns {@code null}.
 *
 * <p>If a non-integer {@code Retry-After} ever arrives (intermediary
 * rewriting the header into RFC 7231's HTTP-date form, or a server
 * contract change), the SDK fails loudly with a
 * {@code GoodmemException} at the call site rather than silently
 * dropping the value — see {@code HttpExecutor.parseRetryAfter()}
 * for how to add support for additional formats when that fires.
 */
public class RateLimitException extends ApiException {
    private final Long retryAfterSeconds;

    public RateLimitException(String message, int statusCode, String body) {
        this(message, statusCode, body, null);
    }

    public RateLimitException(String message, int statusCode, String body, Long retryAfterSeconds) {
        super(message, statusCode, body);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * Parsed {@code Retry-After} response header in seconds, or
     * {@code null} when the server did not send the header.
     *
     * <p>A present-but-unparseable header (e.g. an HTTP-date instead
     * of delta-seconds) does <em>not</em> reach this getter — per
     * {@code HttpExecutor.parseRetryAfter}, an unparseable value
     * throws {@code GoodmemException} at the call site before this
     * {@code RateLimitException} is ever constructed. So {@code null}
     * means only "header absent," never "header unparseable."
     */
    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
