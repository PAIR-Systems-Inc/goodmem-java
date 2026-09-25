package ai.pairsys.goodmem.client.errors;

/**
 * HTTP 404 Not Found — the resource referenced by the request id
 * doesn't exist (or was deleted). Idempotent {@code delete} retries
 * may legitimately encounter this; call sites that treat delete as
 * fire-and-forget should swallow it.
 */
public class NotFoundException extends ApiException {
    public NotFoundException(String message, int statusCode, String body) {
        super(message, statusCode, body);
    }
}
