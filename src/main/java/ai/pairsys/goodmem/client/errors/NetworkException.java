package ai.pairsys.goodmem.client.errors;

/**
 * Wraps a transport-level failure (DNS, connect, read/write, timeout,
 * mid-stream drop). Mirrors Python's {@code NetworkError}: callers can
 * catch transport failures specifically without losing the
 * {@link GoodmemException} parent type, so existing
 * {@code catch (GoodmemException)} branches still work.
 *
 * <p>The original {@link java.io.IOException} (or other transport
 * cause) is attached as {@link #getCause()}.
 */
public class NetworkException extends GoodmemException {
    public NetworkException(String message) {
        super(message);
    }

    public NetworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
