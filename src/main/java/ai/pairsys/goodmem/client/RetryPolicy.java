package ai.pairsys.goodmem.client;

import java.time.Duration;
import java.util.Objects;

/** SDK retry configuration. Wake timeout limits retry starts, not active attempts. */
public final class RetryPolicy {
    private final Duration wakeTimeout;

    private RetryPolicy(Duration wakeTimeout) {
        this.wakeTimeout = Objects.requireNonNull(wakeTimeout, "wakeTimeout");
        if (wakeTimeout.isNegative()) throw new IllegalArgumentException("wakeTimeout must not be negative");
        wakeTimeout.toNanos(); // Reject values that cannot be represented by the monotonic clock.
    }

    public Duration wakeTimeout() { return wakeTimeout; }
    public static RetryPolicy defaults() { return builder().build(); }
    public static RetryPolicy none() { return new RetryPolicy(Duration.ZERO); }
    public static Builder builder() { return new Builder(); }
    public Builder toBuilder() { return builder().wakeTimeout(wakeTimeout); }

    public static final class Builder {
        private Duration wakeTimeout = Duration.ofSeconds(180);
        private Builder() {}
        public Builder wakeTimeout(Duration timeout) { wakeTimeout = timeout; return this; }
        public RetryPolicy build() { return new RetryPolicy(wakeTimeout); }
    }
}
