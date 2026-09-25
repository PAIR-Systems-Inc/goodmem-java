package ai.pairsys.goodmem.client;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;

/**
 * Lazy paginator over a GoodMem list endpoint.
 *
 * <p>Returned by methods like {@code client.spaces.list(...)} instead of the
 * raw list-response record. Exposes the current page's items plus a
 * cross-page {@link Iterable} view that auto-fetches subsequent pages as
 * iteration proceeds — mirrors Python's {@code Paginator[T]} behavior.
 *
 * <p>Usage:
 * <pre>{@code
 * // Typed options form (preferred): IDE-completable filter fields.
 * Page<Space> page = client.spaces.list(
 *     SpaceListOptions.builder().maxResults(50).build());
 *
 * // Just the first page
 * List<Space> items = page.items();
 *
 * // All pages, lazily fetched
 * for (Space s : page) {
 *     process(s);
 * }
 *
 * // Manual paging
 * while (page.hasMore()) {
 *     page = page.next();
 *     // ...
 * }
 *
 * // Map-based escape hatch for wire keys not yet on the typed options:
 * Page<Space> raw = client.spaces.listRaw(Map.of("maxResults", 50));
 * }</pre>
 *
 * @param <T> element type (e.g. {@code Space}, {@code Memory})
 */
public final class Page<T> implements Iterable<T> {

    private final List<T> items;
    private final String nextToken;
    private final Function<String, Page<T>> fetchNext;

    Page(List<T> items, String nextToken, Function<String, Page<T>> fetchNext) {
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
     * Fetch the next page.
     *
     * @throws NoSuchElementException when {@link #hasMore()} is false
     */
    public Page<T> next() {
        if (!hasMore()) {
            throw new NoSuchElementException("No more pages");
        }
        return fetchNext.apply(nextToken);
    }

    /**
     * Iterate lazily across every page. Each element is fetched at most once;
     * network calls happen only when the current page's items are exhausted.
     */
    @Override
    public Iterator<T> iterator() {
        return iterator(Long.MAX_VALUE);
    }

    /**
     * Iterate lazily across every page, capped at {@code maxItems} total
     * elements. Mirrors Python's {@code page_size=...} / {@code max_items=...}
     * convenience pair: pass {@code maxResults} on the options class to
     * control per-page size; pass {@code maxItems} here to cap total
     * iteration. Pages stop being fetched as soon as the cap is reached.
     *
     * @param maxItems total cap across all pages; pass {@link Long#MAX_VALUE}
     *                 (or use the no-arg {@link #iterator()}) for uncapped
     *                 iteration. Negative values raise.
     */
    public Iterator<T> iterator(long maxItems) {
        if (maxItems < 0) {
            throw new IllegalArgumentException(
                "maxItems must be >= 0, got " + maxItems);
        }
        return new Iterator<>() {
            private Page<T> current = Page.this;
            private Iterator<T> cursor = current.items.iterator();
            private long emitted = 0;

            @Override
            public boolean hasNext() {
                if (emitted >= maxItems) return false;
                while (!cursor.hasNext() && current.hasMore()) {
                    current = current.next();
                    cursor = current.items.iterator();
                }
                return cursor.hasNext();
            }

            @Override
            public T next() {
                if (!hasNext()) throw new NoSuchElementException();
                emitted++;
                return cursor.next();
            }
        };
    }

    /**
     * Build a {@code Page<T>} from a query-driven REST fetcher. The returned
     * page carries a closure that knows how to fetch subsequent pages by
     * merging {@code nextToken} into {@code query}.
     *
     * <p>Used by generated Tier-2 code — not typically called by SDK users.
     */
    public static <T, R> Page<T> forQuery(
            Map<String, Object> query,
            Function<Map<String, Object>, R> fetcher,
            Function<R, List<T>> itemsExtractor,
            Function<R, String> tokenExtractor
    ) {
        R resp = fetcher.apply(query);
        List<T> items = itemsExtractor.apply(resp);
        String token = tokenExtractor.apply(resp);
        Function<String, Page<T>> fetchNext = nextToken -> {
            Map<String, Object> nextQuery = new LinkedHashMap<>(query == null ? Map.of() : query);
            nextQuery.put("nextToken", nextToken);
            return forQuery(nextQuery, fetcher, itemsExtractor, tokenExtractor);
        };
        return new Page<>(items, token, fetchNext);
    }
}
