package ai.pairsys.goodmem.client.internal.transforms;

import ai.pairsys.goodmem.client.models.ApiKeyAuth;
import ai.pairsys.goodmem.client.models.CredentialKind;
import ai.pairsys.goodmem.client.models.EndpointAuthentication;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Language-agnostic convenience transforms re-implemented for Java.
 *
 * <p>The declarative kinds in {@code convenience.json} (e.g. {@code api_key_to_credentials},
 * {@code registry_lookup}, {@code endpoint_inference}, {@code credential_check}) are applied
 * by the generated Tier-3 code by calling static methods on this class. The Python SDK's
 * {@code _transforms.py} implements the same kinds against pydantic models; this class is
 * its Java counterpart against records.
 */
public final class Transforms {
    private Transforms() {}

    /**
     * Wrap a bare API key string into the server's {@code EndpointAuthentication} structure.
     *
     * <p>Mirrors the Python {@code api_key_to_credentials} transform. Returns {@code null}
     * when the input is {@code null} or empty, so the caller can detect "no credentials
     * provided" and leave the request field unset.
     *
     * @param apiKey raw provider API key (e.g. {@code "sk-..."}); may be null
     * @return populated {@link EndpointAuthentication}, or {@code null} if no key was given
     */
    public static EndpointAuthentication apiKeyToCredentials(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return null;
        }
        ApiKeyAuth key = new ApiKeyAuth(apiKey, null, null, null);
        return new EndpointAuthentication(CredentialKind.CREDENTIAL_KIND_API_KEY, key, null, null);
    }

    /**
     * Look up a registry entry for a model identifier. Returns {@code null} when
     * the model is unknown — callers decide whether to treat that as "user
     * must provide the fields manually" or an error.
     *
     * @param registryType one of {@code "embedders"}, {@code "llms"}, {@code "rerankers"}
     * @param modelIdentifier the model id (e.g. {@code "text-embedding-3-large"})
     */
    public static Map<String, Object> registryLookup(String registryType, String modelIdentifier) {
        if (modelIdentifier == null || modelIdentifier.isEmpty()) {
            return null;
        }
        switch (registryType) {
            case "embedders": return Registries.lookupEmbedder(modelIdentifier);
            case "llms":      return Registries.lookupLlm(modelIdentifier);
            case "rerankers": return Registries.lookupReranker(modelIdentifier);
            default:
                throw new IllegalArgumentException("Unknown registry type: " + registryType);
        }
    }

    /**
     * Return the default base URL for a provider, or {@code null} when the
     * provider is not in the registry.
     *
     * <p>Mirrors Python's {@code endpoint_inference} transform.
     */
    public static String inferEndpoint(String providerType) {
        if (providerType == null) return null;
        Map<String, Object> entry = Registries.lookupProvider(providerType);
        if (entry == null) return null;
        Object url = entry.get("default_base_url");
        return url == null ? null : url.toString();
    }

    /**
     * Extract the registry's default output dimensions for a model. Returns
     * {@code null} if unknown.
     *
     * <p>Mirrors Python's {@code dimensions_to_dimensionality} transform.
     */
    public static Long dimensionsDefault(String registryType, String modelIdentifier) {
        Map<String, Object> entry = registryLookup(registryType, modelIdentifier);
        if (entry == null) return null;
        Object dims = entry.get("dimensions");
        if (!(dims instanceof Map<?, ?> dimsMap)) return null;
        Object def = dimsMap.get("default");
        if (def instanceof Number n) return n.longValue();
        return null;
    }

    /**
     * Validate that credentials are provided when the target endpoint is a
     * SaaS host that requires them. Throws {@link IllegalArgumentException}
     * when an API-key-requiring URL is targeted without credentials.
     *
     * <p>Mirrors Python's {@code credential_check} transform. Runs last in
     * the convenience pipeline because it depends on {@code providerType}
     * (filled by registry_lookup) and {@code endpointUrl} (filled by
     * endpoint_inference) already being resolved.
     *
     * @param providerType provider identifier (e.g. {@code "OPENAI"})
     * @param endpointUrl resolved endpoint URL
     * @param credentials already-built credentials, or {@code null}
     */
    public static void credentialCheck(
            String providerType,
            String endpointUrl,
            EndpointAuthentication credentials
    ) {
        if (credentials != null) return;
        if (providerType == null || endpointUrl == null) return;
        Map<String, Object> provider = Registries.lookupProvider(providerType);
        if (provider == null) return;
        Object required = provider.get("base_urls_requiring_api_key");
        if (!(required instanceof List<?> list)) return;
        // ``inv-convenience-transforms-behavior-parity``. The comparison
        // is HOSTNAME-based, not full-URL — Python's
        // ``urlparse(endpoint_url).hostname`` and MCP's
        // ``new URL(endpoint_url).hostname`` both match by host only, so
        // common variations (trailing slash, explicit port, path variant,
        // case differences) all correctly trigger the credential check.
        // Audit 2026-05-20 caught Java doing full-URL string-equals,
        // which silently bypassed the check on every variant and
        // dispatched keyless requests that surfaced as a server 401
        // instead of the SDK's call-site IAE. Mirror Python/MCP
        // semantics here.
        String endpointHost = hostnameOf(endpointUrl);
        if (endpointHost == null) return;
        for (Object u : list) {
            String saasHost = hostnameOf(String.valueOf(u));
            if (saasHost == null) continue;
            if (endpointHost.equalsIgnoreCase(saasHost)) {
                // Caller-input validation — use IllegalArgumentException
                // per ``inv-exception-types-mapping``. GoodmemException
                // is reserved for SDK-side / server-side failures; the
                // caller missing an API key is a caller-fault arg error.
                throw new IllegalArgumentException(
                    "Provider '" + providerType + "' at '" + endpointUrl
                    + "' requires credentials. Pass apiKey to the convenience"
                    + " overload or set request.credentials() before calling create.");
            }
        }
    }

    /**
     * Extracts the hostname from a URL string, mirroring Python's
     * {@code urlparse(url).hostname} semantics. Returns {@code null}
     * when the input doesn't parse or has no host component — callers
     * fall through without throwing rather than risk false positives.
     */
    private static String hostnameOf(String url) {
        if (url == null || url.isEmpty()) return null;
        try {
            String host = java.net.URI.create(url).getHost();
            return host == null ? null : host.toLowerCase(java.util.Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
