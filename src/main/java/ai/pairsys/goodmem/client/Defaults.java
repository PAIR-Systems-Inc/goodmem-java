package ai.pairsys.goodmem.client;

import ai.pairsys.goodmem.client.models.ChunkingConfiguration;
import ai.pairsys.goodmem.client.models.LengthMeasurement;
import ai.pairsys.goodmem.client.models.RecursiveChunkingConfiguration;
import ai.pairsys.goodmem.client.models.SeparatorKeepStrategy;

/**
 * SDK-side default values referenced by the generated convenience layer.
 *
 * <p>Mirrors Python's {@code from goodmem import DEFAULT_CHUNKING_CONFIG}
 * pattern: constants here are wired into the emitted Tier-3 overrides by
 * looking up {@code value_key} in {@code convenience.json}'s
 * {@code defaults} block. Users can also reference them directly when
 * passing a {@link ai.pairsys.goodmem.client.models.SpaceCreationRequest} without
 * going through the convenience overload.
 */
public final class Defaults {
    private Defaults() {}

    /**
     * Matches Python SDK's {@code DEFAULT_CHUNKING_CONFIG} — recursive
     * chunking at 512 chars with 64-char overlap, KEEP_END boundary
     * strategy, measured by characters.
     */
    public static final ChunkingConfiguration DEFAULT_CHUNKING_CONFIG =
        new ChunkingConfiguration(
            null,
            new RecursiveChunkingConfiguration(
                // ``chunk_size`` / ``chunk_overlap`` are int32 in the
                // OpenAPI spec — cycle-14 int-width fix narrowed both to
                // Java ``Integer``. Use boxed Integer literals.
                Integer.valueOf(512),
                Integer.valueOf(64),
                null,
                SeparatorKeepStrategy.KEEP_END,
                null,
                LengthMeasurement.CHARACTER_COUNT
            ),
            null
        );
}
