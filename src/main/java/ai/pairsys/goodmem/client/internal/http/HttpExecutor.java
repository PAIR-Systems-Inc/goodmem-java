package ai.pairsys.goodmem.client.internal.http;

import com.fasterxml.jackson.core.type.TypeReference;
import ai.pairsys.goodmem.client.errors.ApiException;
import ai.pairsys.goodmem.client.errors.AuthenticationException;
import ai.pairsys.goodmem.client.errors.BadRequestException;
import ai.pairsys.goodmem.client.errors.ConflictException;
import ai.pairsys.goodmem.client.errors.GoodmemException;
import ai.pairsys.goodmem.client.errors.InternalServerException;
import ai.pairsys.goodmem.client.errors.NetworkException;
import ai.pairsys.goodmem.client.errors.NotFoundException;
import ai.pairsys.goodmem.client.errors.PermissionDeniedException;
import ai.pairsys.goodmem.client.errors.RateLimitException;
import ai.pairsys.goodmem.client.errors.UnprocessableEntityException;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class HttpExecutor {
    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http;
    private final HttpUrl baseUrl;
    private final Map<String, String> defaultHeaders;
    private final int maxStreamLineBytes;

    public HttpExecutor(OkHttpClient http, HttpUrl baseUrl, Map<String, String> defaultHeaders) {
        // 0 → the stream reader falls back to
        // {@link ai.pairsys.goodmem.client.EventStream#DEFAULT_STREAM_MAX_LINE_BYTES}.
        this(http, baseUrl, defaultHeaders, 0);
    }

    public HttpExecutor(
        OkHttpClient http,
        HttpUrl baseUrl,
        Map<String, String> defaultHeaders,
        int maxStreamLineBytes) {
        this.http = http;
        this.baseUrl = baseUrl;
        this.defaultHeaders = defaultHeaders;
        this.maxStreamLineBytes = maxStreamLineBytes;
    }

    /**
     * Read timeout (milliseconds) of the underlying {@link OkHttpClient}.
     * Tier-3 overrides for long-running endpoints ({@code admin.drain},
     * {@code ping.once}) consult this to fail fast when the call's body-budget
     * would exceed the configured client timeout — see
     * {@code inv-budget-exceeds-client-timeout-fails-fast}.
     *
     * <p>Connection-level concerns are managed at the client, not per-method.
     * Returning {@code 0} means "no read timeout" in OkHttp; callers treat that
     * as "no upper bound" (any budget is fine).
     */
    public int readTimeoutMillis() {
        return http.readTimeoutMillis();
    }

    public <T> T get(String path, Class<T> responseType) {
        return get(path, null, responseType);
    }

    public <T> T get(String path, Map<String, ?> query, Class<T> responseType) {
        Request req = newBuilder(path, query).get().build();
        return execute(req, responseType, null);
    }

    public <T> T get(String path, TypeReference<T> responseType) {
        Request req = newBuilder(path, null).get().build();
        return execute(req, null, responseType);
    }

    public <T> T post(String path, Object body, Class<T> responseType) {
        Request req = newBuilder(path, null).post(jsonBody(body)).build();
        return execute(req, responseType, null);
    }

    public <T> T put(String path, Object body, Class<T> responseType) {
        Request req = newBuilder(path, null).put(jsonBody(body)).build();
        return execute(req, responseType, null);
    }

    public <T> T patch(String path, Object body, Class<T> responseType) {
        Request req = newBuilder(path, null).patch(jsonBody(body)).build();
        return execute(req, responseType, null);
    }

    public byte[] getBytes(String path, Map<String, ?> query) {
        Request req = newBuilder(path, query).get().build();
        try (Response response = http.newCall(req).execute()) {
            int status = response.code();
            ResponseBody body = response.body();
            if (status >= 400) {
                throw mapError(status, body == null ? "" : body.string(), response);
            }
            return body == null ? new byte[0] : body.bytes();
        } catch (IOException e) {
            throw new NetworkException("HTTP request failed", e);
        }
    }

    public void delete(String path) {
        Request req = newBuilder(path, null).delete().build();
        execute(req, Void.class, null);
    }

    public <T> T delete(String path, Object body, Class<T> responseType) {
        // body == null is a legitimate "send no body" signal for endpoints
        // whose OpenAPI declares requestBody.required: false (issue #1036).
        // Without this branch, jsonBody(null) would NPE or serialize "null".
        Request.Builder builder = newBuilder(path, null);
        Request req = body == null ? builder.delete().build()
                                    : builder.delete(jsonBody(body)).build();
        return execute(req, responseType, null);
    }

    // ------------------------------------------------------------------
    // Async variants — bridge OkHttp's Call.enqueue(callback) → CompletableFuture
    // ------------------------------------------------------------------

    public <T> CompletableFuture<T> getAsync(String path, Class<T> responseType) {
        return getAsync(path, null, responseType);
    }

    public <T> CompletableFuture<T> getAsync(String path, Map<String, ?> query, Class<T> responseType) {
        Request req = newBuilder(path, query).get().build();
        return executeAsync(req, responseType);
    }

    public <T> CompletableFuture<T> postAsync(String path, Object body, Class<T> responseType) {
        RequestBody rb;
        try {
            rb = jsonBody(body);
        } catch (GoodmemException e) {
            // Serialization failure is an outbound-request error, not a
            // call-site validation error — async callers expect it to land
            // via the future so {@code .exceptionally()} catches it.
            // Arg-validation (IAE / NPE) still throws synchronously per
            // {@code inv-async-error-contract}.
            return CompletableFuture.failedFuture(e);
        }
        Request req = newBuilder(path, null).post(rb).build();
        return executeAsync(req, responseType);
    }

    public <T> CompletableFuture<T> putAsync(String path, Object body, Class<T> responseType) {
        RequestBody rb;
        try {
            rb = jsonBody(body);
        } catch (GoodmemException e) {
            return CompletableFuture.failedFuture(e);
        }
        Request req = newBuilder(path, null).put(rb).build();
        return executeAsync(req, responseType);
    }

    public <T> CompletableFuture<T> patchAsync(String path, Object body, Class<T> responseType) {
        RequestBody rb;
        try {
            rb = jsonBody(body);
        } catch (GoodmemException e) {
            return CompletableFuture.failedFuture(e);
        }
        Request req = newBuilder(path, null).patch(rb).build();
        return executeAsync(req, responseType);
    }

    public CompletableFuture<byte[]> getBytesAsync(String path, Map<String, ?> query) {
        Request req = newBuilder(path, query).get().build();
        CompletableFuture<byte[]> fut = new CompletableFuture<>();
        Call call = http.newCall(req);
        call.enqueue(new Callback() {
            @Override public void onFailure(Call c, IOException e) {
                fut.completeExceptionally(new NetworkException("HTTP request failed", e));
            }
            @Override public void onResponse(Call c, Response response) {
                try (Response r = response) {
                    int status = r.code();
                    ResponseBody body = r.body();
                    if (status >= 400) {
                        String err = body == null ? "" : body.string();
                        fut.completeExceptionally(safeMapError(status, err, r));
                        return;
                    }
                    fut.complete(body == null ? new byte[0] : body.bytes());
                } catch (IOException e) {
                    fut.completeExceptionally(new NetworkException("HTTP request failed", e));
                }
            }
        });
        propagateCancel(fut, call);
        return fut;
    }

    public CompletableFuture<Void> deleteAsync(String path) {
        Request req = newBuilder(path, null).delete().build();
        return executeAsync(req, Void.class).thenApply(v -> null);
    }

    public <T> CompletableFuture<T> deleteAsync(String path, Object body, Class<T> responseType) {
        // Mirror the sync sibling: body == null sends no body (issue #1036).
        Request req;
        if (body == null) {
            req = newBuilder(path, null).delete().build();
        } else {
            RequestBody rb;
            try {
                rb = jsonBody(body);
            } catch (GoodmemException e) {
                return CompletableFuture.failedFuture(e);
            }
            req = newBuilder(path, null).delete(rb).build();
        }
        return executeAsync(req, responseType);
    }

    /**
     * POST and open a streaming {@link Response} for caller-driven reading. The
     * future completes once headers arrive; the stream body must be consumed +
     * closed by the caller (typically via the stream-wrapper class that owns
     * the response).
     */
    public CompletableFuture<StreamResponse> openStreamAsync(String path, Object body) {
        RequestBody rb;
        try {
            rb = jsonBody(body);
        } catch (GoodmemException e) {
            return CompletableFuture.failedFuture(e);
        }
        Request req = newBuilder(path, null).post(rb).build();
        CompletableFuture<StreamResponse> fut = new CompletableFuture<>();
        Call call = http.newCall(req);
        call.enqueue(new Callback() {
            @Override public void onFailure(Call c, IOException e) {
                fut.completeExceptionally(new NetworkException("HTTP request failed", e));
            }
            @Override public void onResponse(Call c, Response response) {
                int status = response.code();
                if (status >= 400) {
                    try (ResponseBody rb = response.body()) {
                        String err = rb == null ? "" : rb.string();
                        fut.completeExceptionally(safeMapError(status, err, response));
                    } catch (IOException e) {
                        fut.completeExceptionally(new NetworkException("HTTP request failed", e));
                    }
                    return;
                }
                // Cycle 13 (2026-05-23) audit finding: if the caller invoked
                // ``fut.cancel(true)`` between OkHttp entering ``onResponse``
                // and the dispatcher reaching here, the future is already
                // terminal, ``complete(response)`` returns false, and the
                // Response (holding a connection-pool slot until closed)
                // is orphaned. ``propagateCancel``'s ``call.cancel()``
                // releases the OkHttp Call, NOT an already-arrived
                // Response. Close the Response when we lose the race.
                // ``inv-openstream-completes-or-closes-response``.
                if (!fut.complete(new StreamResponse(response, maxStreamLineBytes))) {
                    try { response.close(); } catch (Exception ignored) {}
                }
            }
        });
        propagateCancel(fut, call);
        return fut;
    }

    private <T> CompletableFuture<T> executeAsync(Request req, Class<T> clazz) {
        CompletableFuture<T> fut = new CompletableFuture<>();
        Call call = http.newCall(req);
        call.enqueue(new Callback() {
            @Override public void onFailure(Call c, IOException e) {
                fut.completeExceptionally(new NetworkException("HTTP request failed", e));
            }
            @Override public void onResponse(Call c, Response response) {
                try (Response r = response) {
                    int status = r.code();
                    ResponseBody body = r.body();
                    String bodyString = body == null ? "" : body.string();
                    if (status >= 400) {
                        fut.completeExceptionally(safeMapError(status, bodyString, r));
                        return;
                    }
                    if (status == 204 || bodyString.isEmpty() || clazz == Void.class) {
                        @SuppressWarnings("unchecked")
                        T v = (T) null;
                        fut.complete(v);
                        return;
                    }
                    try {
                        fut.complete(Json.MAPPER.readValue(bodyString, clazz));
                    } catch (com.fasterxml.jackson.core.JsonProcessingException jpe) {
                        // Response parse failure — not a transport
                        // error. See execute() above. ``inv-exception-
                        // types-mapping``.
                        String snippet = bodyString.length() > 200
                            ? bodyString.substring(0, 200) : bodyString;
                        fut.completeExceptionally(
                            new GoodmemException(
                                "Malformed response body: " + snippet, jpe));
                    }
                } catch (IOException e) {
                    // Transport IOException (read body string, etc.).
                    fut.completeExceptionally(new NetworkException("HTTP request failed", e));
                }
            }
        });
        propagateCancel(fut, call);
        return fut;
    }

    /**
     * Wrap {@link #mapError} so any {@link RuntimeException} the error-mapper
     * itself throws is surfaced via {@link CompletableFuture#completeExceptionally}
     * instead of escaping the async callback. {@link #parseRetryAfter} throws
     * {@link ai.pairsys.goodmem.client.errors.GoodmemException} (a
     * {@code RuntimeException}) on a non-delta-seconds {@code Retry-After}
     * header — deliberate "fail loudly" design — but in OkHttp's async
     * dispatcher path that exception would escape {@code onResponse},
     * be swallowed by the dispatcher, and leave the future never completed.
     * Caller on {@code .get()} / {@code .join()} would block indefinitely
     * — strictly worse than the sync path's clean propagation. Audit 2026-05-31
     * caught this. See {@code inv-async-error-contract} /
     * {@code inv-async-maperror-never-hangs}.
     */
    private Throwable safeMapError(int status, String bodyString, Response response) {
        try {
            return mapError(status, bodyString, response);
        } catch (RuntimeException ex) {
            return ex;
        }
    }

    /**
     * Wire {@code CompletableFuture.cancel(true)} through to the underlying OkHttp
     * {@link Call} so a cancelled future actually stops the in-flight request
     * (releases the connection back to the pool, fires onFailure with
     * {@code IOException("Canceled")}). Without this, {@code cancel(true)} would
     * mark the future done but the HTTP request would continue in the
     * background — industry baseline, but worth the one-line upgrade.
     */
    private static <T> void propagateCancel(CompletableFuture<T> fut, Call call) {
        fut.whenComplete((v, e) -> {
            if (fut.isCancelled() && !call.isCanceled()) call.cancel();
        });
    }

    /**
     * POST a JSON body and return the raw {@link Response} for the caller to
     * read as an NDJSON / SSE stream. The caller <strong>must</strong> close
     * the returned response — typically via try-with-resources on the
     * stream wrapper that owns it.
     *
     * <p>Surfaces 4xx/5xx immediately (closes the body and throws); only
     * successful responses are returned. Readers don't need to re-check the
     * status.
     */
    public StreamResponse openStream(String path, Object body) {
        Request req = newBuilder(path, null).post(jsonBody(body)).build();
        try {
            Response response = http.newCall(req).execute();
            int status = response.code();
            if (status >= 400) {
                try (ResponseBody rb = response.body()) {
                    String err = rb == null ? "" : rb.string();
                    throw mapError(status, err, response);
                }
            }
            return new StreamResponse(response, maxStreamLineBytes);
        } catch (IOException e) {
            throw new NetworkException("HTTP request failed", e);
        }
    }

    private RequestBody jsonBody(Object body) {
        return body == null
            ? RequestBody.create(new byte[0], JSON_MEDIA)
            : RequestBody.create(toJsonBytes(body), JSON_MEDIA);
    }

    private Request.Builder newBuilder(String path, Map<String, ?> query) {
        // Path is emitter-controlled — no current caller passes null. The
        // explicit guard converts a future emitter regression into a clean
        // message at the framing boundary instead of an opaque NPE one
        // frame deeper in ``path.startsWith("/")``. Sibling to
        // ``inv-null-guard-universal`` (internal-plumbing variant).
        java.util.Objects.requireNonNull(path, "path");
        // {@code encodedPath(path)} would REPLACE baseUrl's path component, so
        // a base URL with a mount prefix ({@code http://example.com/api}) loses
        // its prefix on every request. Append instead: keep baseUrl's
        // segments, then add {@code path}'s segments after.
        HttpUrl.Builder urlBuilder = baseUrl.newBuilder();
        String trimmed = path.startsWith("/") ? path.substring(1) : path;
        if (!trimmed.isEmpty()) {
            urlBuilder.addEncodedPathSegments(trimmed);
        }
        if (query != null) {
            query.forEach((k, v) -> {
                if (v != null) urlBuilder.addQueryParameter(k, String.valueOf(v));
            });
        }
        Request.Builder b = new Request.Builder().url(urlBuilder.build());
        defaultHeaders.forEach(b::header);
        return b;
    }

    private byte[] toJsonBytes(Object body) {
        try {
            return Json.MAPPER.writeValueAsBytes(body);
        } catch (IOException e) {
            throw new GoodmemException("Failed to serialize request body", e);
        }
    }

    private <T> T execute(Request req, Class<T> clazz, TypeReference<T> typeRef) {
        try (Response response = http.newCall(req).execute()) {
            int status = response.code();
            ResponseBody responseBody = response.body();
            String bodyString = responseBody == null ? "" : responseBody.string();

            if (status >= 400) {
                throw mapError(status, bodyString, response);
            }
            if (status == 204 || bodyString.isEmpty()) {
                return null;
            }
            try {
                if (clazz != null) {
                    return Json.MAPPER.readValue(bodyString, clazz);
                }
                return Json.MAPPER.readValue(bodyString, typeRef);
            } catch (com.fasterxml.jackson.core.JsonProcessingException jpe) {
                // Response parse failures are NOT transport errors. The
                // HTTP round-trip succeeded; the server's body just
                // didn't match the expected schema. ``inv-exception-
                // types-mapping`` says map this to GoodmemException
                // (parent of all SDK-side failures) so users
                // catch(NetworkException) doesn't accidentally absorb a
                // parse bug. Mirrors EventStream's malformed-NDJSON
                // handling.
                String snippet = bodyString.length() > 200
                    ? bodyString.substring(0, 200) : bodyString;
                throw new GoodmemException(
                    "Malformed response body: " + snippet, jpe);
            }
        } catch (IOException e) {
            // Genuine transport IOException — connection drops, socket
            // timeouts, SSL handshake failures, DNS errors. ``inv-
            // exception-types-mapping``.
            throw new NetworkException("HTTP request failed", e);
        }
    }

    private ApiException mapError(int status, String body, Response response) {
        // The exception's {@code getMessage()} must surface the server's
        // explanation so {@code log.error(exc)} / {@code str(exc)} shows
        // something actionable instead of a bare {@code HTTP 400}. Mirrors
        // Python's {@code raise_for_status} which already does this. The
        // full body is still available via {@code getBody()}; we just
        // truncate the message-embedded copy at 500 chars to keep
        // log lines reasonable.
        String msg;
        if (body == null || body.isEmpty()) {
            msg = "HTTP " + status;
        } else {
            String snippet = body.length() > 500 ? body.substring(0, 500) : body;
            msg = "HTTP " + status + ": " + snippet;
        }
        if (status == 429) {
            return new RateLimitException(msg, status, body, parseRetryAfter(response));
        }
        return switch (status) {
            case 400 -> new BadRequestException(msg, status, body);
            case 401 -> new AuthenticationException(msg, status, body);
            case 403 -> new PermissionDeniedException(msg, status, body);
            case 404 -> new NotFoundException(msg, status, body);
            case 409 -> new ConflictException(msg, status, body);
            case 422 -> new UnprocessableEntityException(msg, status, body);
            default -> status >= 500
                ? new InternalServerException(msg, status, body)
                : new ApiException(msg, status, body);
        };
    }

    /**
     * Parse the {@code Retry-After} response header as integer seconds
     * (RFC 7231 delta-seconds form). Returns {@code null} when the
     * header is absent (which is the common case on goodmem 429s that
     * don't carry a rate-limit verdict). Throws {@link GoodmemException}
     * loudly when the header is present but not parseable as integer
     * seconds — see the inline comment for context.
     */
    private static Long parseRetryAfter(Response response) {
        if (response == null) return null;
        String raw = response.header("Retry-After");
        if (raw == null || raw.isEmpty()) return null;
        String trimmed = raw.trim();
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            // The goodmem server only sends Retry-After in
            // delta-seconds form — see
            // {@code server/.../RateLimitingInterceptor.java:76} (gRPC
            // trailer is {@code Long.toString(seconds)}) and
            // {@code server/.../GrpcErrorHandler.java:290} (REST header
            // copies the trailer verbatim). RFC 7231 also permits an
            // HTTP-date form, but goodmem never emits it.
            //
            // Hitting this branch means EITHER (a) an intermediary
            // (CDN, reverse proxy, load balancer) rewrote our header
            // into a form goodmem doesn't produce, OR (b) the server
            // contract has changed. Silent {@code null} here would mask
            // either bug for months; throw loudly so we get triggered
            // to fix.
            //
            // To add support for a new format when this fires: insert
            // a parse arm before the throw. The most likely new
            // format is HTTP-date (RFC 1123):
            //   try {
            //       java.time.ZonedDateTime when =
            //           java.time.ZonedDateTime.parse(
            //               trimmed,
            //               java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME);
            //       return Math.max(0L, java.time.Duration.between(
            //           java.time.ZonedDateTime.now(), when).toSeconds());
            //   } catch (java.time.format.DateTimeParseException dtpe) {
            //       // fall through to the throw below
            //   }
            throw new GoodmemException(
                "Retry-After header value \"" + trimmed
                + "\" is not in delta-seconds form. Goodmem server only "
                + "sends integer seconds; an intermediary may have "
                + "rewritten the header or the server contract has "
                + "changed. See parseRetryAfter() in HttpExecutor.java "
                + "for how to add support for additional formats.");
        }
    }
}
