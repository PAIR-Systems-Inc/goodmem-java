package ai.pairsys.goodmem.client.errors;

/**
 * HTTP 400 Bad Request — the server rejected the request shape (a
 * required field was omitted, a value violated a server-side range
 * check, mutually exclusive fields were both set, etc.). The exception
 * message embeds the server's diagnostic text; {@link #getBody()} has
 * the full payload.
 */
public class BadRequestException extends ApiException {
    public BadRequestException(String message, int statusCode, String body) {
        super(message, statusCode, body);
    }
}
