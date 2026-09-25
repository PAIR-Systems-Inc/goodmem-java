package ai.pairsys.goodmem.client.internal.http;

import okhttp3.Response;

/**
 * A streaming HTTP {@link Response} paired with the per-event NDJSON line cap
 * that should bound its reader.
 *
 * <p>{@link HttpExecutor#openStream} and {@link HttpExecutor#openStreamAsync}
 * return this so the stream-wrapper classes ({@code PingStream},
 * {@code RetrieveMemoryStream}) can enforce the configured cap without the
 * generated API layer having to thread the setting through each call site.
 * Mirrors the Go client's {@code streamMaxLineBytes} field and the .NET
 * transport's {@code MaxStreamLineBytes} property, which carry the same value
 * to their stream readers.
 *
 * @param response    the open response; the caller owns closing it
 * @param maxLineBytes per-event byte cap ({@code <= 0} means "use the default")
 */
public record StreamResponse(Response response, int maxLineBytes) {}
