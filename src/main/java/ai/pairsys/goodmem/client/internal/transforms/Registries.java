package ai.pairsys.goodmem.client.internal.transforms;

import com.fasterxml.jackson.core.type.TypeReference;
import ai.pairsys.goodmem.client.errors.GoodmemException;
import ai.pairsys.goodmem.client.internal.http.Json;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

/**
 * Lazy-loaded, immutable view of the model and provider registries bundled on
 * the SDK classpath (copied from {@code clients/registries/*.json} at
 * generation time).
 *
 * <p>Used by the convenience transforms to auto-infer provider, endpoint URL,
 * dimensions, sequence length, and supported modalities from a
 * {@code model_identifier}, and to decide whether an endpoint requires an
 * API key.
 *
 * <p>Registry values are read only once per classloader. Data is effectively
 * constant across an SDK release.
 */
public final class Registries {
    private static final String RESOURCE_ROOT = "/ai/pairsys/goodmem/client/registries/";

    private static final class Holder {
        private static final Map<String, Map<String, Object>> EMBEDDERS =
            load("embedders.json");
        private static final Map<String, Map<String, Object>> LLMS =
            load("llms.json");
        private static final Map<String, Map<String, Object>> RERANKERS =
            load("rerankers.json");
        private static final Map<String, Map<String, Object>> PROVIDERS =
            load("providers.json");
    }

    private Registries() {}

    public static Map<String, Object> lookupEmbedder(String modelId) {
        return Holder.EMBEDDERS.get(modelId);
    }

    public static Map<String, Object> lookupLlm(String modelId) {
        return Holder.LLMS.get(modelId);
    }

    public static Map<String, Object> lookupReranker(String modelId) {
        return Holder.RERANKERS.get(modelId);
    }

    public static Map<String, Object> lookupProvider(String providerType) {
        return Holder.PROVIDERS.get(providerType);
    }

    public static Map<String, Map<String, Object>> allEmbedders() {
        return Collections.unmodifiableMap(Holder.EMBEDDERS);
    }

    public static Map<String, Map<String, Object>> allProviders() {
        return Collections.unmodifiableMap(Holder.PROVIDERS);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> load(String filename) {
        String path = RESOURCE_ROOT + filename;
        try (InputStream in = Registries.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new GoodmemException(
                    "Registry resource not found on classpath: " + path);
            }
            return Json.MAPPER.readValue(
                in,
                new TypeReference<Map<String, Map<String, Object>>>() {}
            );
        } catch (IOException e) {
            throw new GoodmemException("Failed to load registry " + filename, e);
        }
    }
}
