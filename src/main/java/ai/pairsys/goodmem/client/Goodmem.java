package ai.pairsys.goodmem.client;

import ai.pairsys.goodmem.client.api.AdminAPI;
import ai.pairsys.goodmem.client.api.AccessPolicyAPI;
import ai.pairsys.goodmem.client.api.ApikeysAPI;
import ai.pairsys.goodmem.client.api.DecisionModelsAPI;
import ai.pairsys.goodmem.client.api.EmbeddersAPI;
import ai.pairsys.goodmem.client.api.InstanceAPI;
import ai.pairsys.goodmem.client.api.LlmsAPI;
import ai.pairsys.goodmem.client.api.MemoriesAPI;
import ai.pairsys.goodmem.client.api.OcrAPI;
import ai.pairsys.goodmem.client.api.PingAPI;
import ai.pairsys.goodmem.client.api.RerankersAPI;
import ai.pairsys.goodmem.client.api.ServiceIdentitiesAPI;
import ai.pairsys.goodmem.client.api.SpacesAPI;
import ai.pairsys.goodmem.client.api.SystemAPI;
import ai.pairsys.goodmem.client.api.UserEnrollmentsAPI;
import ai.pairsys.goodmem.client.api.UsersAPI;
import ai.pairsys.goodmem.client.internal.http.HttpExecutor;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

/**
 * Synchronous client for the GoodMem REST API. Every namespace
 * ({@link #accessPolicy}, {@link #admin}, {@link #apikeys}, {@link #embedders}, {@link #instance},
 * {@link #llms}, {@link #memories}, {@link #ocr}, {@link #ping}, {@link #rerankers},
 * {@link #serviceIdentities}, {@link #spaces}, {@link #system}, {@link #userEnrollments},
 * {@link #users}) exposes typed
 * methods that return concrete response objects; non-2xx responses
 * raise {@link ai.pairsys.goodmem.client.errors.ApiException} subclasses.
 *
 * <p>Two construction modes:
 * <ul>
 *   <li><b>Simple</b> — pass {@code baseUrl} + {@code apiKey} (and
 *       optionally a {@code timeout}); the client builds its own
 *       {@code OkHttpClient}.</li>
 *   <li><b>BYO</b> — pass a pre-configured {@code OkHttpClient} via
 *       {@link Builder#httpClient(OkHttpClient)}. {@code timeout} is
 *       incompatible with this mode (configure it on your
 *       {@code OkHttpClient} directly).</li>
 * </ul>
 *
 * <pre>{@code
 * try (Goodmem client = Goodmem.builder()
 *         .baseUrl("http://localhost:8080")
 *         .apiKey("gm_...")
 *         .build()) {
 *     SystemInfoResponse info = client.system.info();
 *     System.out.println("Server " + info.version());
 * }
 * }</pre>
 *
 * <p>Errors flow through typed {@link ai.pairsys.goodmem.client.errors.ApiException}
 * subclasses (4xx / 5xx) and {@link ai.pairsys.goodmem.client.errors.NetworkException}
 * for transport failures. Request validation errors (e.g. missing
 * credentials that trip {@code credential_check}) are thrown
 * synchronously before the HTTP call.
 *
 * <p>For non-blocking pipelines, use {@link AsyncGoodmem} — same
 * construction surface, every method returns a
 * {@link java.util.concurrent.CompletableFuture}.
 */
public final class Goodmem implements AutoCloseable {
    private final OkHttpClient http;
    private final boolean ownsHttp;
    public final AccessPolicyAPI accessPolicy;
    public final AdminAPI admin;
    public final ApikeysAPI apikeys;
    /** Decision-model registration and typed evaluation operations. */
    public final DecisionModelsAPI decisionModels;
    public final EmbeddersAPI embedders;
    public final InstanceAPI instance;
    public final LlmsAPI llms;
    public final MemoriesAPI memories;
    public final OcrAPI ocr;
    public final PingAPI ping;
    public final RerankersAPI rerankers;
    public final ServiceIdentitiesAPI serviceIdentities;
    public final SpacesAPI spaces;
    public final SystemAPI system;
    public final UserEnrollmentsAPI userEnrollments;
    public final UsersAPI users;

    private Goodmem(
        OkHttpClient http,
        HttpUrl baseUrl,
        Map<String, String> headers,
        boolean ownsHttp,
        int streamMaxLineBytes) {
        this.http = http;
        this.ownsHttp = ownsHttp;
        HttpExecutor executor = new HttpExecutor(http, baseUrl, headers, streamMaxLineBytes);
        this.accessPolicy = new AccessPolicyAPI(executor);
        this.admin = new AdminAPI(executor);
        this.apikeys = new ApikeysAPI(executor);
        this.decisionModels = new DecisionModelsAPI(executor);
        this.embedders = new EmbeddersAPI(executor);
        this.instance = new InstanceAPI(executor);
        this.llms = new LlmsAPI(executor);
        this.memories = new MemoriesAPI(executor);
        this.ocr = new OcrAPI(executor);
        this.ping = new PingAPI(executor);
        this.rerankers = new RerankersAPI(executor);
        this.serviceIdentities = new ServiceIdentitiesAPI(executor);
        this.spaces = new SpacesAPI(executor);
        this.system = new SystemAPI(executor);
        this.userEnrollments = new UserEnrollmentsAPI(executor);
        this.users = new UsersAPI(executor);
    }

    public static Builder builder() {
        return new Builder();
    }

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

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /** Per-call timeout for the SDK-built {@link OkHttpClient}. Incompatible
         *  with {@link #httpClient(OkHttpClient)} — configure timeouts on your
         *  own client in BYO mode. */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            this.timeoutExplicitlySet = true;
            return this;
        }

        public Builder httpClient(OkHttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        /**
         * Maximum size, in bytes, of a single NDJSON event on the streaming
         * endpoints ({@code memories.retrieve}, {@code ping.stream}). An event
         * larger than this throws
         * {@link ai.pairsys.goodmem.client.errors.GoodmemException} instead of
         * buffering without bound. Defaults to
         * {@link EventStream#DEFAULT_STREAM_MAX_LINE_BYTES} (8 MiB, matching the
         * Go and .NET SDKs); a value {@code <= 0} keeps that default. Compatible
         * with {@link #httpClient(OkHttpClient)} — it's an SDK-level concern, not
         * a transport setting.
         */
        public Builder streamMaxLineBytes(int streamMaxLineBytes) {
            this.streamMaxLineBytes = streamMaxLineBytes;
            return this;
        }

        public Goodmem build() {
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
                return new Goodmem(httpClient, parseBaseUrl(baseUrl), headers, false, streamMaxLineBytes);
            }

            Objects.requireNonNull(baseUrl, "baseUrl is required");
            // ``apiKey`` is optional for bootstrap workflows:
            // ``/v1/system/info`` and ``/v1/system/init`` are
            // server-side unauthenticated — the chicken-and-egg flow
            // that creates the first API key. When ``apiKey`` is null
            // the SDK omits the ``x-api-key`` header; the server's
            // own auth boundary returns 401 → AuthenticationException
            // on every other endpoint.
            // ``inv-bootstrap-endpoints-callable-without-key``.
            //
            // Empty ``baseUrl`` is a SDK-side block: there's no host
            // to POST to. Empty ``apiKey`` is NOT validated here —
            // server returns 401 with a clear message; per the
            // gatekeeping principle we propagate that rather than
            // duplicate the rule. See notes/invariants.yaml::
            // inv-empty-string-required-args.
            if (baseUrl.isEmpty()) {
                throw new IllegalArgumentException("baseUrl must be a non-empty string");
            }
            // Note: deliberately NOT setting {@code callTimeout} — that's a
            // total-elapsed-time budget that aborts long-running streams
            // (memories.retrieve, ping.stream) after {@code timeout} seconds
            // even when bytes are flowing. {@code connectTimeout} + {@code readTimeout}
            // + {@code writeTimeout} cover idle-timeout budgets which are what
            // most callers actually want. See audit cycle 6 finding.
            // Disable redirect-following on the SDK-built client. OkHttp's
            // hard-coded credential strip only covers ``Authorization``;
            // since GoodMem passes ``x-api-key`` as a custom header (see
            // below), a 3xx redirect to a typoed or compromised host
            // would carry the API key across the host boundary. BYO
            // clients (provided via ``Builder#httpClient``) retain full
            // control over redirect policy. amin3141 pre-GA blocker #2,
            // cycle 14 (2026-05-27). ``inv-no-redirect-with-custom-auth-header``.
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
            return new Goodmem(built, parseBaseUrl(baseUrl), headers, true, streamMaxLineBytes);
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
