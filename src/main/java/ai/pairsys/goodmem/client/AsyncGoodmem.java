package ai.pairsys.goodmem.client;

import ai.pairsys.goodmem.client.api.AsyncAdminAPI;
import ai.pairsys.goodmem.client.api.AsyncAccessPolicyAPI;
import ai.pairsys.goodmem.client.api.AsyncApikeysAPI;
import ai.pairsys.goodmem.client.api.AsyncDecisionModelsAPI;
import ai.pairsys.goodmem.client.api.AsyncEmbeddersAPI;
import ai.pairsys.goodmem.client.api.AsyncInstanceAPI;
import ai.pairsys.goodmem.client.api.AsyncLlmsAPI;
import ai.pairsys.goodmem.client.api.AsyncMemoriesAPI;
import ai.pairsys.goodmem.client.api.AsyncOcrAPI;
import ai.pairsys.goodmem.client.api.AsyncPingAPI;
import ai.pairsys.goodmem.client.api.AsyncRerankersAPI;
import ai.pairsys.goodmem.client.api.AsyncServiceIdentitiesAPI;
import ai.pairsys.goodmem.client.api.AsyncSpacesAPI;
import ai.pairsys.goodmem.client.api.AsyncSystemAPI;
import ai.pairsys.goodmem.client.api.AsyncUserEnrollmentsAPI;
import ai.pairsys.goodmem.client.api.AsyncUsersAPI;
import ai.pairsys.goodmem.client.internal.http.HttpExecutor;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

/**
 * Async counterpart to {@link Goodmem}. Every method on every namespace returns
 * a {@link java.util.concurrent.CompletableFuture} instead of the concrete
 * response type, letting callers compose non-blocking pipelines.
 *
 * <p>Identical construction surface to the sync {@link Goodmem} — same Builder,
 * same two mutually-exclusive modes (simple or custom OkHttp client). The
 * underlying HTTP calls use OkHttp's native async callbacks, so no thread pool
 * is needed beyond OkHttp's own dispatcher.
 *
 * <pre>{@code
 * try (AsyncGoodmem client = AsyncGoodmem.builder()
 *         .baseUrl("http://localhost:8080")
 *         .apiKey("gm_...")
 *         .build()) {
 *     client.system.info()
 *         .thenAccept(info -> System.out.println("Server " + info.version()))
 *         .join();
 * }
 * }</pre>
 *
 * <p><b>Error-handling contract</b> (mirrors AWS SDK v2, Google Cloud, and
 * Azure async clients):
 * <ul>
 *   <li><b>Argument-validation errors throw synchronously</b> — null required
 *       args, empty path strings, mutex violations, missing required-companion
 *       fields. These fail before any HTTP call is built and surface as a
 *       regular Java exception ({@link IllegalArgumentException},
 *       {@link NullPointerException}, etc.) at the call site. Catch via
 *       {@code try/catch}, NOT {@code .exceptionally(...)}.</li>
 *   <li><b>HTTP errors complete the future exceptionally</b> — 4xx/5xx
 *       responses wrap as {@link ai.pairsys.goodmem.client.errors.ApiException}
 *       subclasses; transport failures wrap as
 *       {@link ai.pairsys.goodmem.client.errors.NetworkException}; deserialization
 *       errors wrap as {@link ai.pairsys.goodmem.client.errors.GoodmemException}.
 *       Catch via {@code .exceptionally(...)} or {@code .handle(...)}.</li>
 * </ul>
 *
 * <p>The split mirrors what every major Java async SDK does: synchronous
 * validation looks identical to the sync client (no surprise that a null
 * arg throws); runtime errors live in the future where the rest of the
 * async pipeline can compose with them.
 */
public final class AsyncGoodmem implements AutoCloseable {
    private final OkHttpClient http;
    private final boolean ownsHttp;
    public final AsyncAccessPolicyAPI accessPolicy;
    public final AsyncAdminAPI admin;
    public final AsyncApikeysAPI apikeys;
    /** Decision-model registration and typed evaluation operations. */
    public final AsyncDecisionModelsAPI decisionModels;
    public final AsyncEmbeddersAPI embedders;
    public final AsyncInstanceAPI instance;
    public final AsyncLlmsAPI llms;
    public final AsyncMemoriesAPI memories;
    public final AsyncOcrAPI ocr;
    public final AsyncPingAPI ping;
    public final AsyncRerankersAPI rerankers;
    public final AsyncServiceIdentitiesAPI serviceIdentities;
    public final AsyncSpacesAPI spaces;
    public final AsyncSystemAPI system;
    public final AsyncUserEnrollmentsAPI userEnrollments;
    public final AsyncUsersAPI users;

    private AsyncGoodmem(
        OkHttpClient http,
        HttpUrl baseUrl,
        Map<String, String> headers,
        boolean ownsHttp,
        int streamMaxLineBytes) {
        this.http = http;
        this.ownsHttp = ownsHttp;
        HttpExecutor executor = new HttpExecutor(http, baseUrl, headers, streamMaxLineBytes);
        this.accessPolicy = new AsyncAccessPolicyAPI(executor);
        this.admin = new AsyncAdminAPI(executor);
        this.apikeys = new AsyncApikeysAPI(executor);
        this.decisionModels = new AsyncDecisionModelsAPI(executor);
        this.embedders = new AsyncEmbeddersAPI(executor);
        this.instance = new AsyncInstanceAPI(executor);
        this.llms = new AsyncLlmsAPI(executor);
        this.memories = new AsyncMemoriesAPI(executor);
        this.ocr = new AsyncOcrAPI(executor);
        this.ping = new AsyncPingAPI(executor);
        this.rerankers = new AsyncRerankersAPI(executor);
        this.serviceIdentities = new AsyncServiceIdentitiesAPI(executor);
        this.spaces = new AsyncSpacesAPI(executor);
        this.system = new AsyncSystemAPI(executor);
        this.userEnrollments = new AsyncUserEnrollmentsAPI(executor);
        this.users = new AsyncUsersAPI(executor);
    }

    public static Builder builder() { return new Builder(); }

    @Override
    public void close() {
        if (ownsHttp) {
            http.dispatcher().executorService().shutdown();
            http.connectionPool().evictAll();
        }
    }

    public static final class Builder {
        private String baseUrl;
        private String apiKey;
        private Duration timeout = Duration.ofSeconds(30);
        private boolean timeoutExplicitlySet = false;
        private OkHttpClient httpClient;
        private int streamMaxLineBytes = 0; // 0 → EventStream.DEFAULT_STREAM_MAX_LINE_BYTES

        private Builder() {}

        public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }
        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        /** Per-call timeout for the SDK-built {@link OkHttpClient}. Incompatible
         *  with {@link #httpClient(OkHttpClient)}. */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            this.timeoutExplicitlySet = true;
            return this;
        }
        public Builder httpClient(OkHttpClient httpClient) { this.httpClient = httpClient; return this; }
        /** Per-event NDJSON line cap for the streaming endpoints; see
         *  {@link Goodmem.Builder#streamMaxLineBytes(int)}. Defaults to
         *  {@link EventStream#DEFAULT_STREAM_MAX_LINE_BYTES} (8 MiB). */
        public Builder streamMaxLineBytes(int streamMaxLineBytes) {
            this.streamMaxLineBytes = streamMaxLineBytes;
            return this;
        }

        public AsyncGoodmem build() {
            if (httpClient != null) {
                if (baseUrl == null) {
                    throw new IllegalArgumentException(
                        "baseUrl is required even when httpClient is provided");
                }
                if (baseUrl.isEmpty()) {
                    throw new IllegalArgumentException(
                        "baseUrl must be a non-empty string");
                }
                if (timeoutExplicitlySet) {
                    throw new IllegalArgumentException(
                        "timeout(Duration) is incompatible with httpClient(OkHttpClient) — "
                        + "configure the timeout on your OkHttpClient directly.");
                }
                Map<String, String> headers = new LinkedHashMap<>();
                if (apiKey != null) {
                    headers.put("x-api-key", apiKey);
                }
                return new AsyncGoodmem(httpClient, parseBaseUrl(baseUrl), headers, false, streamMaxLineBytes);
            }

            Objects.requireNonNull(baseUrl, "baseUrl is required");
            // ``apiKey`` is optional for bootstrap workflows (see Goodmem
            // for rationale). When null, the SDK omits the ``x-api-key``
            // header; ``/v1/system/info`` and ``/v1/system/init`` succeed
            // unauthenticated, every other endpoint returns 401.
            // ``inv-bootstrap-endpoints-callable-without-key``.
            if (baseUrl.isEmpty()) {
                throw new IllegalArgumentException("baseUrl must be a non-empty string");
            }
            // See Goodmem.Builder.build() for the rationale on omitting
            // {@code callTimeout} — same reasoning, async path. Also
            // disable redirect-following: ``x-api-key`` is a custom header
            // OkHttp doesn't strip on 3xx, so following a redirect would
            // carry the API key across host boundaries. amin3141 pre-GA
            // blocker #2, cycle 14. ``inv-no-redirect-with-custom-auth-header``.
            OkHttpClient built = new OkHttpClient.Builder()
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .followRedirects(false)
                .followSslRedirects(false)
                .build();
            Map<String, String> headers = new HashMap<>();
            if (apiKey != null) {
                headers.put("x-api-key", apiKey);
            }
            return new AsyncGoodmem(built, parseBaseUrl(baseUrl), headers, true, streamMaxLineBytes);
        }

        private static HttpUrl parseBaseUrl(String baseUrl) {
            HttpUrl url = HttpUrl.parse(baseUrl);
            if (url == null) {
                throw new IllegalArgumentException("Invalid base URL: " + baseUrl);
            }
            return url;
        }
    }
}
