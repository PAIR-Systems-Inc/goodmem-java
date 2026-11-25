package ai.pairsys.goodmem.client.internal.http;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/** Retries a cold wake before any successful response is exposed to the caller. */
public final class WakeRetryInterceptor implements Interceptor {
    private final WakeRetryPolicy policy;

    // The SDK buffered this body, but marked it one-shot to prevent OkHttp's
    // own retries after forwarding. A marked pre-forward wake can replay it.
    enum BufferedBody { REPLAYABLE }

    // AsyncWakeRetry owns the retry loop for requests with this tag.
    enum Execution { ASYNC }

    private static final class ResponseRetryHint { String value; }

    /** Keep 503 replay and hint parsing inside our policy. OkHttp otherwise retries
     * zero-delay responses and parses integer hints into a bounded int. */
    public static Response preventAutomaticRetry(Chain chain) throws IOException {
        Response response = chain.proceed(chain.request());
        String hint = response.header("Retry-After");
        ResponseRetryHint original = chain.request().tag(ResponseRetryHint.class);
        if (original != null && response.code() == 503 && hint != null) {
            original.value = hint;
            return response.newBuilder().removeHeader("Retry-After").build();
        }
        return response;
    }

    public WakeRetryInterceptor(Duration budget) { policy = new WakeRetryPolicy(budget); }

    public static Long retryAfterSeconds(String raw) { return WakeRetryPolicy.retryAfterSeconds(raw); }
    long remainingNanos(long started) { return policy.remainingNanos(started); }
    long retryDelay(Request request, Response response, long started, int attempt) {
        return policy.retryDelay(request, response, started, attempt);
    }

    static Response bufferedError(Response response) throws IOException {
        try (response) {
            return response.newBuilder().body(okhttp3.ResponseBody.create(
                response.body().bytes(), response.body().contentType())).build();
        }
    }

    @Override public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        long started = System.nanoTime();
        for (int attempt = 0; ; attempt++) {
            if (chain.call().isCanceled()) throw new IOException("Canceled");
            ResponseRetryHint original = new ResponseRetryHint();
            Response response = chain.proceed(request.newBuilder().tag(ResponseRetryHint.class, original).build());
            if (original.value != null) {
                response = response.newBuilder().header("Retry-After", original.value)
                    .request(response.request().newBuilder().tag(ResponseRetryHint.class, null).build()).build();
            }
            if (request.tag(Execution.class) == Execution.ASYNC) return response;
            long delay = retryDelay(request, response, started, attempt);
            if (delay < 0) return response;
            Response last = bufferedError(response);
            if (delay >= remainingNanos(started)) return last;
            long waitStarted = System.nanoTime();
            while (System.nanoTime() - waitStarted < delay) {
                if (chain.call().isCanceled()) throw new IOException("Canceled");
                long left = delay - (System.nanoTime() - waitStarted);
                try {
                    TimeUnit.NANOSECONDS.sleep(Math.min(Math.max(0, left), TimeUnit.MILLISECONDS.toNanos(50)));
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("wake retry interrupted");
                }
            }
            if (remainingNanos(started) <= 0) return last;
            last.close();
        }
    }
}
