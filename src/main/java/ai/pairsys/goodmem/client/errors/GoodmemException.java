package ai.pairsys.goodmem.client.errors;

/**
 * Root of the GoodMem SDK exception hierarchy. Catch this to handle any
 * SDK-raised failure — HTTP errors ({@link ApiException} and its typed
 * subclasses), transport-level failures ({@link NetworkException}), or
 * SDK-internal validation that the client refuses to send to the
 * server.
 *
 * <p>All SDK exceptions extend {@link RuntimeException} — there are no
 * checked exceptions on the public surface. Async methods complete the
 * returned {@link java.util.concurrent.CompletableFuture} exceptionally
 * with the same types, wrapped in
 * {@link java.util.concurrent.CompletionException} at
 * {@code .get()} / {@code .join()}.
 */
public class GoodmemException extends RuntimeException {
    public GoodmemException(String message) {
        super(message);
    }

    public GoodmemException(String message, Throwable cause) {
        super(message, cause);
    }
}
