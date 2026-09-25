// Hand-written — typed constants for the open-ended PingNotice.code field.
package ai.pairsys.goodmem.client;

/**
 * String constants for the codes the server currently emits on
 * {@code PingNotice#code()}. The proto
 * declaration is intentionally open-ended ({@code string code}, not a
 * closed enum) — the server team may add new codes without coordinating
 * an SDK release — so this class deliberately exposes plain
 * {@code String} constants rather than a Java enum: a server-side
 * addition would not break the SDK.
 *
 * <p>Use as a typed alternative to magic strings:
 * <pre>{@code
 * if (PingNoticeCodes.COUNT_CLAMPED.equals(notice.code())) {
 *     long requested = Long.parseLong(notice.details().get("requested"));
 *     long applied = Long.parseLong(notice.details().get("applied"));
 *     log.warn("Ping count clamped from {} to {}", requested, applied);
 * }
 * }</pre>
 *
 * <p>If the server emits a code not listed here, the SDK still
 * surfaces it as a raw {@code String} via
 * {@code PingNotice#code()} — handle the
 * unknown case in a {@code default} branch.
 */
public final class PingNoticeCodes {
    private PingNoticeCodes() {}

    /** Ping control actions are not supported in this server build
     *  (e.g., the server received a control RPC it cannot honor). */
    public static final String CONTROL_UNSUPPORTED = "CONTROL_UNSUPPORTED";

    /** Probe count was reduced to the maximum allowed by the server.
     *  {@code details.requested} and {@code details.applied} carry the
     *  numeric values. */
    public static final String COUNT_CLAMPED = "COUNT_CLAMPED";

    /** {@code maxInFlight} was reduced to the per-server cap (currently
     *  {@code 1} on the streaming endpoint). */
    public static final String CONCURRENCY_CLAMPED = "CONCURRENCY_CLAMPED";

    /** {@code payloadSizeBytes} was reduced to the maximum allowed.
     *  {@code details.requested} and {@code details.applied} carry the
     *  numeric values. */
    public static final String PAYLOAD_CLAMPED = "PAYLOAD_CLAMPED";
}
