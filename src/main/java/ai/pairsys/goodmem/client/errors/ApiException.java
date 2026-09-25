package ai.pairsys.goodmem.client.errors;

/**
 * Base class for any HTTP 4xx / 5xx returned by the GoodMem server.
 * Concrete subclasses cover the common statuses
 * ({@link BadRequestException} 400, {@link AuthenticationException} 401,
 * {@link PermissionDeniedException} 403, {@link NotFoundException} 404,
 * {@link ConflictException} 409, {@link UnprocessableEntityException}
 * 422, {@link RateLimitException} 429, {@link InternalServerException}
 * 5xx); a status that doesn't match a typed subclass is raised as a
 * plain {@code ApiException}.
 *
 * <p>The exception message embeds the server's response body (truncated
 * to a reasonable length so log lines stay manageable). Use
 * {@link #getBody()} to recover the full payload and
 * {@link #getStatusCode()} for branching on the numeric status. All
 * subclasses extend {@link GoodmemException}, so existing
 * {@code catch (GoodmemException)} sites still trigger.
 *
 * <p>Status-specific response headers (e.g. {@code Retry-After} on
 * 429) are surfaced by the relevant typed subclass — see
 * {@link RateLimitException#getRetryAfterSeconds()}. The generic
 * {@code ApiException} does not capture response headers beyond the
 * status code and body.
 */
public class ApiException extends GoodmemException {
    private final int statusCode;
    private final String body;

    public ApiException(String message, int statusCode, String body) {
        super(message);
        this.statusCode = statusCode;
        this.body = body;
    }

    /** HTTP status code from the server response. */
    public int getStatusCode() {
        return statusCode;
    }

    /** Raw response body (full payload). */
    public String getBody() {
        return body;
    }
}
