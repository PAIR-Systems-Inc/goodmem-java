package ai.pairsys.goodmem.client.internal.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class Json {
    public static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new Jdk8Module())
        .registerModule(new JavaTimeModule())
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        // Server enum additions are wire-backward-compatible (the server
        // team rightly considers adding a new ``MemoryProcessingStatus``
        // value a non-breaking change). Without this flag, every
        // released SDK throws ``InvalidFormatException`` on response
        // deserialization the day the server ships the new value — a
        // hard failure that loses the whole response, not just the
        // unknown field. Mapping unknown enum values to ``null`` lets
        // the caller pattern-match the known values it cares about; the
        // caller still can choose to fail loudly on null if their
        // use-case requires it. amin3141 pre-GA blocker #3 (cycle 14,
        // 2026-05-27). ``inv-unknown-server-enum-coerces-not-crash``.
        .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);

    private Json() {}
}
