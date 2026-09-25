package ai.pairsys.goodmem.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Async counterpart to {@link Page}. Returned by async list methods; each page's
 * items are available directly, and subsequent pages are fetched via a
 * {@link CompletableFuture}.
 *
 * <p>Typical use:
 * <pre>{@code
 * // Typed-options form (preferred):
 * asyncClient.spaces.list(SpaceListOptions.builder().maxResults(50).build())
 *     .thenCompose(page -> collectAll(page, new ArrayList<>()))
 *     .thenAccept(allSpaces -> { // ... });
 *
 * static CompletableFuture<List<Space>> collectAll(AsyncPage<Space> page, List<Space> acc) {
 *     acc.addAll(page.items());
 *     return page.hasMore() ? page.next().thenCompose(p -> collectAll(p, acc))
 *                           : CompletableFuture.completedFuture(acc);
 * }
 *
 * // Map-based escape hatch for wire keys not yet on the typed options:
 * asyncClient.spaces.listRaw(Map.of("maxResults", 50))
 *     .thenAccept(page -> { // ... });
 * }</pre>
 *
 * Unlike the sync {@link Page}, there's no {@link Iterable} shape — Java 21
 * doesn't ship a clean async-iterator primitive, and mixing blocking iteration
 * with async I/O is a footgun. Use {@code next()}-chaining with
 * {@link CompletableFuture#thenCompose(Function)} instead.
 *
 * @param <T> element type
 */
public final class AsyncPage<T> {

    private final List<T> items;
    private final String nextToken;
    private final Function<String, CompletableFuture<AsyncPage<T>>> fetchNext;

    AsyncPage(List<T> items, String nextToken, Function<String, CompletableFuture<AsyncPage<T>>> fetchNext) {
        this.items = items == null ? Collections.emptyList() : items;
        this.nextToken = nextToken;
        this.fetchNext = fetchNext;
    }

    /** Items in the current page. */
    public List<T> items() { return items; }

    /** Server-supplied continuation token, or {@code null} when exhausted. */
    public String nextToken() { return nextToken; }

    /** True when another page is available. */
    public boolean hasMore() {
        return nextToken != null && !nextToken.isEmpty();
    }

    /**
     * Fetch the next page. Returns a future that completes exceptionally
     * when {@link #hasMore()} is false.
     */
    public CompletableFuture<AsyncPage<T>> next() {
        if (!hasMore()) {
            CompletableFuture<AsyncPage<T>> failed = new CompletableFuture<>();
            failed.completeExceptionally(new java.util.NoSuchElementException("No more pages"));
            return failed;
        }
        return fetchNext.apply(nextToken);
    }

    /**
     * Build an {@code AsyncPage<T>} from a query-driven async fetcher. The
     * returned page's {@code next()} recursively chains subsequent pages via
     * the same fetcher, merging {@code nextToken} into the query.
     *
     * <p>Used by generated Tier-2 async code — not typically called by SDK users.
     */
    public static <T, R> CompletableFuture<AsyncPage<T>> forQuery(
            Map<String, Object> query,
            Function<Map<String, Object>, CompletableFuture<R>> fetcher,
            Function<R, List<T>> itemsExtractor,
            Function<R, String> tokenExtractor
    ) {
        return fetcher.apply(query).thenApply(resp -> {
            List<T> items = itemsExtractor.apply(resp);
            String token = tokenExtractor.apply(resp);
            Function<String, CompletableFuture<AsyncPage<T>>> fetchNext = nextToken -> {
                Map<String, Object> nextQuery = new LinkedHashMap<>(query == null ? Map.of() : query);
                nextQuery.put("nextToken", nextToken);
                return forQuery(nextQuery, fetcher, itemsExtractor, tokenExtractor);
            };
            return new AsyncPage<>(items, token, fetchNext);
        });
    }
}
