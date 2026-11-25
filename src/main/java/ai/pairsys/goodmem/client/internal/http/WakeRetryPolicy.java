package ai.pairsys.goodmem.client.internal.http;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import okhttp3.Request;
import okhttp3.Response;

/** Pure eligibility and delay calculation shared by sync and async transports. */
final class WakeRetryPolicy {
    private static final DateTimeFormatter HTTP_DATE = DateTimeFormatter
        .ofPattern("EEE, dd MMM uuuu HH:mm:ss 'GMT'", Locale.US).withZone(java.time.ZoneOffset.UTC);
    private final long budgetNanos;

    WakeRetryPolicy(Duration budget) {
        if (budget.isNegative()) throw new IllegalArgumentException("wake timeout must not be negative");
        budgetNanos = budget.toNanos();
    }

    static Long retryAfterSeconds(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        try {
            if (value.matches("[0-9]+")) {
                long seconds = Long.parseLong(value);
                return seconds <= 9007199254740991L ? seconds : null;
            }
            if (!value.matches("[A-Z][a-z]{2}, [0-9]{2} [A-Z][a-z]{2} [0-9]{4} [0-9]{2}:[0-9]{2}:[0-9]{2} GMT")) return null;
            Instant target = ZonedDateTime.parse(value, HTTP_DATE).toInstant();
            if (!HTTP_DATE.format(target).equals(value)) return null;
            return Math.max(0, (long) Math.ceil(Duration.between(Instant.now(), target).toMillis() / 1000.0));
        } catch (RuntimeException ignored) { return null; }
    }

    long remainingNanos(long started) { return budgetNanos - (System.nanoTime() - started); }

    long retryDelay(Request request, Response response, long started, int attempt) {
        Long hint = retryAfterSeconds(response.header("Retry-After"));
        boolean replayable = request.body() == null || (!request.body().isDuplex() &&
            (!request.body().isOneShot() || request.tag(WakeRetryInterceptor.BufferedBody.class) != null));
        if (response.code() != 503 || hint == null || !replayable ||
            !"retry".equals(response.header("X-Goodmem-Activator-Wake"))) return -1;
        long remaining = remainingNanos(started);
        if (remaining <= 0 || hint > TimeUnit.NANOSECONDS.toSeconds(remaining)) return -1;
        long delay = TimeUnit.SECONDS.toNanos(Math.max(hint, 1L << Math.min(attempt, 4)));
        return delay < remaining ? delay : -1;
    }
}
