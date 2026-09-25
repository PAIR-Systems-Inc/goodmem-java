package ai.pairsys.goodmem.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import ai.pairsys.goodmem.client.errors.GoodmemException;
import ai.pairsys.goodmem.client.errors.NetworkException;
import ai.pairsys.goodmem.client.internal.http.Json;
import ai.pairsys.goodmem.client.internal.http.StreamResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.NoSuchElementException;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Iterable NDJSON event stream over an HTTP response body.
 *
 * <p>Each line of the response body is one JSON event. Iteration is lazy —
 * the reader advances one line at a time and the underlying connection
 * stays open until the stream is closed. Always wrap iteration in
 * try-with-resources so connections are released:
 *
 * <pre>{@code
 * try (RetrieveMemoryStream events = client.memories.retrieve(req)) {
 *     for (RetrieveMemoryEvent e : events) {
 *         process(e);
 *     }
 * }
 * }</pre>
 *
 * <p>This is a generic base for endpoint-specific subclasses
 * ({@link RetrieveMemoryStream}, {@link PingStream}, …). The subclass
 * exists purely so callers don't have to spell out a generic parameter
 * at the call site (matches Python's named stream classes).
 *
 * <p>Single-use: re-iterating after the iterator has been obtained throws
 * {@link IllegalStateException}. Malformed lines throw
 * {@link GoodmemException} with a snippet of the offending payload so
 * mid-stream parse failures are debuggable without re-running the request.
 *
 * <p>Each NDJSON event is bounded to a per-event byte cap
 * ({@link #DEFAULT_STREAM_MAX_LINE_BYTES} by default, configurable via
 * {@code Goodmem.builder().streamMaxLineBytes(...)}). A single line that
 * exceeds the cap throws {@link GoodmemException} rather than buffering
 * without limit — an oversized event (e.g. an inlined document body when
 * {@code fetchMemoryContent} is set) would otherwise grow memory without
 * bound, since {@link java.io.BufferedReader#readLine()} has no size limit.
 * Mirrors the Go SDK's {@code bufio.Scanner} cap and the .NET SDK's
 * {@code BoundedLineReader}.
 */
public abstract class EventStream<E> implements Iterable<E>, AutoCloseable {

    /**
     * Default per-event NDJSON line cap when the client doesn't set
     * {@code streamMaxLineBytes}: 8 MiB, matching the Go and .NET SDKs. A single
     * event is buffered in memory before it is parsed, so this bounds worst-case
     * allocation.
     */
    public static final int DEFAULT_STREAM_MAX_LINE_BYTES = 8 * 1024 * 1024;

    private final Response response;
    private final ResponseBody body;
    private final BoundedLineReader reader;
    private final Class<E> eventType;
    private final int maxLineBytes;
    private boolean consumed;

    protected EventStream(StreamResponse source, Class<E> eventType) {
        this.response = source.response();
        this.maxLineBytes =
            source.maxLineBytes() > 0 ? source.maxLineBytes() : DEFAULT_STREAM_MAX_LINE_BYTES;
        ResponseBody _body = response.body();
        if (_body == null) {
            // Best-effort: close the Response before throwing so we don't
            // leak the connection-pool slot. OkHttp's
            // {@code Response.close()} requires a non-null body and
            // itself throws {@code IllegalStateException} when called
            // with no body to release. In the synthetic null-body case
            // there's nothing to release; swallow that secondary throw
            // so the user sees the meaningful
            // {@code GoodmemException("Streaming response had no body")},
            // not the OkHttp invariant. Audit cycle 2026-05-13.
            try {
                response.close();
            } catch (IllegalStateException ignored) {
                // No body → no socket-holding to release → no leak.
            }
            throw new GoodmemException("Streaming response had no body");
        }
        this.body = _body;
        this.reader = new BoundedLineReader(body.byteStream(), this.maxLineBytes);
        this.eventType = eventType;
    }

    @Override
    public Iterator<E> iterator() {
        if (consumed) {
            throw new IllegalStateException(
                getClass().getSimpleName()
                + " can only be iterated once — re-call the originating method to restart");
        }
        consumed = true;
        return new EventIterator();
    }

    @Override
    public void close() {
        response.close();
    }

    private final class EventIterator implements Iterator<E> {
        private E nextEvent;
        private boolean ended;

        @Override
        public boolean hasNext() {
            if (nextEvent != null) return true;
            if (ended) return false;
            String line = null;
            try {
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    // Skip empty server frames (e.g. {@code {}} or
                    // {@code {"retrievedItem":{}}}) that arise when the
                    // server's proto-to-REST mapping emits an
                    // {@code EVENT_NOT_SET} / {@code RESULT_NOT_SET} /
                    // {@code KIND_NOT_SET} placeholder. The skip happens
                    // PRE-parse so the user iterator never sees the
                    // useless frame, regardless of whether the event
                    // record carries a compact-ctor oneOf gate
                    // ({@code PingEvent} still does) or not
                    // ({@code RetrieveMemoryEvent} dropped its gate
                    // 2026-05-14 — the OpenAPI schema declares
                    // {@code type: object} with five nullable variant
                    // fields, "exactly one" lives only in the
                    // description prose; mutex-on-response was
                    // unilaterally enforced by Java with no
                    // Python/MCP equivalent, see issue #994 item 6).
                    // Preferred fix is server-side — stop emitting
                    // empty events for the {@code *_NOT_SET} cases
                    // (issue #994 item 1).
                    // Only the JsonNode-based ``_looksAllNull`` heuristic
                    // is used — it parses the line and confirms every top-
                    // level field is null or an empty object. Pure string-
                    // prefix shortcuts (``equals("{}")`` etc.) were retired
                    // because in pre-parse position they false-positive on
                    // malformed JSON like ``{not valid json}`` and the
                    // iterator would silently skip the bad line instead of
                    // surfacing a ``Malformed NDJSON event`` error.
                    if (_looksAllNull(line)) {
                        continue;
                    }
                    nextEvent = Json.MAPPER.readValue(line, eventType);
                    return true;
                }
                ended = true;
                return false;
            } catch (JsonProcessingException e) {
                String snippet = line == null
                    ? "<null>"
                    : line.substring(0, Math.min(line.length(), 200));
                throw new GoodmemException("Malformed NDJSON event: " + snippet, e);
            } catch (IOException e) {
                // Mid-stream connection drops are TRANSPORT errors, not parse
                // errors — must surface as NetworkException so users who
                // {@code catch (NetworkException)} see all transport failures
                // uniformly. Mirrors Python's Stream wrapping httpx errors.
                //
                // Note: an over-cap line surfaces as an (unchecked)
                // {@link GoodmemException} thrown from
                // {@link BoundedLineReader#readLine()} — an SDK-side limit, not
                // a transport failure — so it propagates past this catch
                // untouched, exactly like the Go/.NET readers.
                throw new NetworkException("Error reading NDJSON stream", e);
            }
        }

        /** Detect an event payload whose top-level fields are all
         *  null or empty-object — the wire shape the server emits for
         *  {@code *_NOT_SET} oneof cases. Conservative parse: only treats
         *  a JSON object with no scalar / non-empty children as
         *  all-null. */
        private boolean _looksAllNull(String line) {
            try {
                com.fasterxml.jackson.databind.JsonNode node =
                    Json.MAPPER.readTree(line);
                if (node == null || !node.isObject()) return false;
                java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = node.elements();
                while (it.hasNext()) {
                    com.fasterxml.jackson.databind.JsonNode child = it.next();
                    if (child.isNull()) continue;
                    if (child.isObject() && child.isEmpty()) continue;
                    return false;
                }
                return true;
            } catch (IOException e) {
                return false;
            }
        }

        @Override
        public E next() {
            if (!hasNext()) throw new NoSuchElementException();
            E e = nextEvent;
            nextEvent = null;
            return e;
        }
    }

    /**
     * Reads NDJSON lines from a byte stream with a hard per-line byte cap.
     * {@link java.io.BufferedReader#readLine()} has no length bound, so a single
     * huge event would otherwise buffer without limit. A line whose bytes reach
     * the cap before a newline throws a {@link GoodmemException} naming the cap
     * and the workaround. Ports the Go SDK's {@code bufio.Scanner} line cap and
     * the .NET SDK's {@code BoundedLineReader}.
     */
    private static final class BoundedLineReader {
        private final InputStream in;
        private final int maxLineBytes;
        private final byte[] readBuf = new byte[16 * 1024];
        private final ByteArrayOutputStream line = new ByteArrayOutputStream(256);
        private int pos; // next unconsumed byte in readBuf
        private int len; // valid bytes in readBuf (-1 once EOF is reached)

        BoundedLineReader(InputStream in, int maxLineBytes) {
            this.in = in;
            this.maxLineBytes = maxLineBytes;
        }

        /**
         * The next line — bytes up to (excluding) the next {@code \n}, UTF-8
         * decoded with a trailing {@code \r} stripped — or {@code null} at end of
         * stream. A transport read fault propagates as {@link IOException} (the
         * caller wraps it as a {@link NetworkException}); an over-cap line throws
         * a {@link GoodmemException}.
         */
        String readLine() throws IOException {
            line.reset();
            while (true) {
                if (pos >= len) {
                    len = in.read(readBuf);
                    pos = 0;
                    if (len < 0) {
                        // EOF: emit a trailing partial line if any, else done.
                        return line.size() > 0 ? decode() : null;
                    }
                    continue;
                }
                int nl = indexOf(readBuf, pos, len, (byte) '\n');
                if (nl < 0) {
                    append(pos, len - pos); // no newline in this chunk — accumulate
                    pos = len;
                    continue;
                }
                append(pos, nl - pos);
                pos = nl + 1; // consume the newline
                return decode();
            }
        }

        private void append(int start, int count) {
            if (line.size() + count > maxLineBytes) {
                throw new GoodmemException(
                    "stream event exceeded the " + maxLineBytes
                        + "-byte per-event line cap; raise it via "
                        + "Goodmem.builder().streamMaxLineBytes(...), or avoid inlining large "
                        + "bodies by leaving RetrieveMemoryRequest.fetchMemoryContent unset and "
                        + "fetching content via the memory content endpoint");
            }
            line.write(readBuf, start, count);
        }

        private String decode() {
            byte[] buf = line.toByteArray();
            int n = buf.length;
            if (n > 0 && buf[n - 1] == '\r') n--; // BufferedReader-style \r\n handling
            return new String(buf, 0, n, StandardCharsets.UTF_8);
        }

        private static int indexOf(byte[] a, int from, int to, byte target) {
            for (int i = from; i < to; i++) {
                if (a[i] == target) return i;
            }
            return -1;
        }
    }
}
