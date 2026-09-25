package ai.pairsys.goodmem.client;

import ai.pairsys.goodmem.client.models.RetrieveMemoryEvent;
import ai.pairsys.goodmem.client.internal.http.StreamResponse;

/**
 * Iterable NDJSON stream of {@link RetrieveMemoryEvent}s from
 * {@code POST /v1/memories:retrieve}. See {@link EventStream} for the
 * iteration contract.
 *
 * <p>Mirrors Python's {@code RetrieveMemoryStream} context-manager
 * convention.
 */
public final class RetrieveMemoryStream extends EventStream<RetrieveMemoryEvent> {

    public RetrieveMemoryStream(StreamResponse source) {
        super(source, RetrieveMemoryEvent.class);
    }
}
