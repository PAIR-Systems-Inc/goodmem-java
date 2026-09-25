package ai.pairsys.goodmem.client;

import ai.pairsys.goodmem.client.models.PingEvent;
import ai.pairsys.goodmem.client.internal.http.StreamResponse;

/**
 * Iterable NDJSON stream of {@link PingEvent}s from
 * {@code POST /v1/ping:stream}. See {@link EventStream} for the iteration
 * contract.
 */
public final class PingStream extends EventStream<PingEvent> {

    public PingStream(StreamResponse source) {
        super(source, PingEvent.class);
    }
}
