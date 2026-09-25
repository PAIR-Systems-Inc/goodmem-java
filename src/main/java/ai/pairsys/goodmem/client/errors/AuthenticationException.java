package ai.pairsys.goodmem.client.errors;

/**
 * HTTP 401 Unauthorized — the API key is missing, malformed, or has
 * been revoked. Construct a fresh {@code Goodmem} client with a valid
 * key and retry.
 */
public class AuthenticationException extends ApiException {
    public AuthenticationException(String message, int statusCode, String body) {
        super(message, statusCode, body);
    }
}
