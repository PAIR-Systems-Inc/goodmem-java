// Hand-written — typed wrapper for gRPC canonical status codes.
package ai.pairsys.goodmem.client;

import java.util.Map;

/**
 * Typed wrapper for a gRPC canonical status code. Use it to pattern-match
 * on the well-known 0–16 codes without losing forward-compatibility when
 * the server surfaces a value this SDK doesn't yet know about:
 *
 * <pre>{@code
 * GrpcCode code = GrpcCode.of(rpcStatus.code());
 * switch (code.known()) {
 *     case OK -> ...
 *     case NOT_FOUND -> ...
 *     default -> ...  // includes Known.UNKNOWN_CODE for server-new values
 * }
 * }</pre>
 *
 * The underlying record carries the raw numeric value so callers who just
 * want the int can still use {@link #value()} directly.
 */
public record GrpcCode(long value, Known known) {

    /** The gRPC canonical codes (grpc.io/docs/guides/status-codes). The
     *  {@code UNKNOWN_CODE} variant is emitted by this SDK when the
     *  server returns a numeric code outside 0–16 — *not* to be confused
     *  with gRPC's own {@code UNKNOWN} (value 2). */
    public enum Known {
        OK(0),
        CANCELLED(1),
        UNKNOWN(2),
        INVALID_ARGUMENT(3),
        DEADLINE_EXCEEDED(4),
        NOT_FOUND(5),
        ALREADY_EXISTS(6),
        PERMISSION_DENIED(7),
        RESOURCE_EXHAUSTED(8),
        FAILED_PRECONDITION(9),
        ABORTED(10),
        OUT_OF_RANGE(11),
        UNIMPLEMENTED(12),
        INTERNAL(13),
        UNAVAILABLE(14),
        DATA_LOSS(15),
        UNAUTHENTICATED(16),
        /** Sentinel for codes not in the canonical 0–16 range. */
        UNKNOWN_CODE(-1);

        public final int value;
        Known(int value) { this.value = value; }
    }

    private static final Map<Long, Known> _BY_VALUE;
    static {
        Map<Long, Known> m = new java.util.HashMap<>();
        for (Known k : Known.values()) {
            if (k != Known.UNKNOWN_CODE) m.put((long) k.value, k);
        }
        _BY_VALUE = Map.copyOf(m);
    }

    /** Wrap a raw numeric code. Unrecognised values get {@link Known#UNKNOWN_CODE}. */
    public static GrpcCode of(long value) {
        return new GrpcCode(value, _BY_VALUE.getOrDefault(value, Known.UNKNOWN_CODE));
    }

    /** Wrap a {@link Known} enum value. */
    public static GrpcCode of(Known known) {
        return new GrpcCode(known.value, known);
    }

    /** True if this code is in the gRPC canonical 0–16 range. */
    public boolean isKnown() {
        return known != Known.UNKNOWN_CODE;
    }

    @Override
    public String toString() {
        // Use the constant's real name (UNKNOWN_CODE) for the out-of-range
        // sentinel so the distinction the class Javadoc preserves carries
        // through to log output. ``UNKNOWN(99)`` would invite the exact
        // conflation with gRPC's canonical UNKNOWN (always value 2) that
        // the Known enum was named to prevent. Audit 2026-05-31 caught this.
        return isKnown() ? known.name() + "(" + value + ")" : "UNKNOWN_CODE(" + value + ")";
    }
}
