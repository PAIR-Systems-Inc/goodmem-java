package ai.pairsys.goodmem.client.errors;

/**
 * HTTP 403 Forbidden — the API key authenticates but lacks the
 * permission required for this operation (e.g. attempting
 * {@code CREATE_MEMORY_ANY} without admin scope, or accessing a
 * private space owned by another user without
 * {@code READ_MEMORY_ANY}). Distinct from
 * {@link AuthenticationException}, which means the credential itself
 * is invalid.
 */
public class PermissionDeniedException extends ApiException {
    public PermissionDeniedException(String message, int statusCode, String body) {
        super(message, statusCode, body);
    }
}
