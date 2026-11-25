package ai.pairsys.goodmem.client.internal.http;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Schedules separate HTTP attempts without holding an OkHttp dispatcher slot during backoff. */
final class AsyncWakeRetry implements Callback {
    private static final ScheduledThreadPoolExecutor TIMER = new ScheduledThreadPoolExecutor(1, r -> {
        Thread thread = new Thread(r, "goodmem-wake-retry");
        thread.setDaemon(true);
        return thread;
    });
    static { TIMER.setRemoveOnCancelPolicy(true); }

    private final OkHttpClient http;
    private final WakeRetryInterceptor policy;
    private final Request request;
    private final CompletableFuture<?> future;
    private final Callback callback;
    private final ScheduledThreadPoolExecutor timer;
    private final long started = System.nanoTime();
    // Guard transitions so cancellation cannot miss a new call or scheduled retry.
    private Call current;
    private ScheduledFuture<?> retry;
    private Response last;
    private int attempt;
    private boolean stopped;

    AsyncWakeRetry(OkHttpClient http, WakeRetryInterceptor policy, Request request,
                   CompletableFuture<?> future, Callback callback) {
        this(http, policy, request, future, callback, TIMER);
    }

    AsyncWakeRetry(OkHttpClient http, WakeRetryInterceptor policy, Request request,
                   CompletableFuture<?> future, Callback callback, ScheduledThreadPoolExecutor timer) {
        this.http = http;
        this.policy = policy;
        this.request = request.newBuilder()
            .tag(WakeRetryInterceptor.Execution.class, WakeRetryInterceptor.Execution.ASYNC).build();
        this.future = future;
        this.callback = callback;
        this.timer = timer;
    }

    void start() {
        future.whenComplete((value, error) -> cleanup());
        enqueue();
    }

    private synchronized void cleanup() {
        stopped = true;
        cancelTimers();
        if (future.isCancelled() && current != null) current.cancel();
    }

    private void cancelTimers() {
        if (retry != null) retry.cancel(false);
        if (last != null) { last.close(); last = null; }
    }

    private synchronized void enqueue() {
        if (stopped || future.isDone()) return;
        if (attempt > 0 && policy.remainingNanos(started) <= 0) {
            stopped = true;
            Response response = last;
            last = null;
            CompletableFuture.runAsync(() -> {
                try { callback.onResponse(current, response); }
                catch (IOException error) { callback.onFailure(current, error); }
            });
            return;
        }
        if (last != null) { last.close(); last = null; }
        current = http.newCall(request);
        // enqueue() starts no synchronous HTTP work; the timer never waits for a response.
        try {
            current.enqueue(this);
        } catch (RuntimeException error) {
            stopped = true;
            cancelTimers();
            CompletableFuture.runAsync(() -> future.completeExceptionally(error));
        }
    }

    @Override public void onFailure(Call call, IOException error) {
        synchronized (this) {
            if (stopped || future.isDone()) return;
            stopped = true;
            cancelTimers();
        }
        // OkHttp can reject enqueue synchronously after its executor shuts down.
        // Keep failure continuations off the timer in that path as well.
        CompletableFuture.runAsync(() -> callback.onFailure(call, error));
    }

    @Override public void onResponse(Call call, Response response) throws IOException {
        long delay;
        synchronized (this) {
            if (stopped || future.isDone()) {
                response.close();
                return;
            }
            delay = policy.retryDelay(request, response, started, attempt);
        }
        if (delay >= 0) {
            // Reading may block. Do not hold the state lock: cancellation must
            // still reach the active call and abort a stalled error body.
            try {
                response = WakeRetryInterceptor.bufferedError(response);
            } catch (IOException error) {
                onFailure(call, error);
                return;
            }
        }
        synchronized (this) {
            if (stopped || future.isDone()) {
                response.close();
                return;
            }
            if (delay >= 0) delay = policy.retryDelay(request, response, started, attempt);
            if (delay >= 0) {
                last = response;
                attempt++;
                retry = timer.schedule(this::enqueue, delay, TimeUnit.NANOSECONDS);
                return;
            }
            stopped = true;
            cancelTimers();
        }
        callback.onResponse(call, response);
    }
}
