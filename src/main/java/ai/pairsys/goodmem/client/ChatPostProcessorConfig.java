// Hand-written — typed configuration for the built-in ChatPostProcessor.
package ai.pairsys.goodmem.client;

import ai.pairsys.goodmem.client.models.LlmId;
import ai.pairsys.goodmem.client.models.PostProcessor;
import ai.pairsys.goodmem.client.models.RerankerId;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed, IDE-completion-friendly configuration for the built-in
 * {@code ChatPostProcessor} — the factory used for retrieval-augmented
 * generation pipelines. Wraps the underlying {@link PostProcessor}
 * {@code name} + {@code config} pair so callers can skip the
 * stringly-typed {@code Map<String, Object>} path.
 *
 * <p>Wire field names match the server's canonical
 * {@code post_processor_flat_keys} (see {@code convenience.json}):
 * {@code llm_id}, {@code reranker_id}, {@code llm_temp},
 * {@code gen_token_budget}, {@code relevance_threshold},
 * {@code max_results}, {@code prompt}, {@code sys_prompt},
 * {@code chronological_resort}.
 *
 * <pre>{@code
 * PostProcessor pp = ChatPostProcessorConfig.builder()
 *     .llmId(llmId)
 *     .genTokenBudget(2048L)
 *     .llmTemp(0.2)
 *     .build()
 *     .toPostProcessor();
 * }</pre>
 *
 * For non-built-in processors, keep using
 * {@code new PostProcessor(fullyQualifiedClassName, configMap)} directly.
 *
 * @param llmId               LLM id (UUID-as-string) to use for RAG-style
 *                            generation; setting this OR {@code rerankerId}
 *                            activates the post-processor pipeline.
 * @param rerankerId          Reranker id; activates rerank scoring on the
 *                            retrieved hits when paired with retrieval.
 * @param llmTemp             LLM sampling temperature; range {@code [0.0, 2.0]}.
 *                            Builder validates and rejects out-of-range
 *                            with {@link IllegalArgumentException}.
 *                            Requires {@code llmId}.
 * @param genTokenBudget      Maximum generation tokens for the LLM call.
 *                            Requires {@code llmId}.
 * @param relevanceThreshold  Minimum reranker score for a hit to survive
 *                            into the LLM context. Requires {@code rerankerId}.
 * @param maxResults          Maximum number of hits the pipeline retains
 *                            before passing to the LLM. Requires at least one
 *                            of {@code llmId} / {@code rerankerId}.
 * @param prompt              Per-call prompt suffix appended to the LLM
 *                            context. Requires {@code llmId}.
 * @param sysPrompt           System prompt prepended to the LLM context.
 *                            Requires {@code llmId}.
 * @param chronologicalResort Re-sort hits by creation time before passing
 *                            to the LLM. Requires at least one of
 *                            {@code llmId} / {@code rerankerId}.
 */
public record ChatPostProcessorConfig(
    String llmId,
    String rerankerId,
    Double llmTemp,
    Long genTokenBudget,
    Double relevanceThreshold,
    Long maxResults,
    String prompt,
    String sysPrompt,
    Boolean chronologicalResort
) {
    /**
     * Compact-constructor activating-key gate. Mirrors {@link Builder#build()}
     * so the rule fires on EVERY construction path — direct positional ctor,
     * Builder.build(), AND Jackson deserialization. Cycle 6 audit caught the
     * Builder-only check (positional construction would silently produce an
     * invalid instance with orphan flat params).
     *
     * <p>Cycle 8 expanded the check to be category-aware (LLM-bound /
     * reranker-bound / pipeline-bound) so users get actionable diagnostics
     * regardless of which activating key they did set. See
     * {@code inv-sdk-convenience-validates-at-earliest}: the flat-to-nested
     * mapping happens client-side, so the server never sees these
     * user-facing field names — the abstraction is ours, so the diagnostics
     * are ours.
     */
    public ChatPostProcessorConfig {
        validateOrphans(
            llmId, rerankerId,
            llmTemp, genTokenBudget, relevanceThreshold,
            maxResults, prompt, sysPrompt, chronologicalResort
        );
    }

    /**
     * Categorized orphan-key gate, shared by the record's compact constructor
     * AND {@link Builder#build()} so callers get the same diagnostic regardless
     * of construction path. Three categories:
     *
     * <ul>
     *   <li><b>LLM-bound</b> ({@code llmTemp}, {@code genTokenBudget}, {@code prompt},
     *       {@code sysPrompt}) — the server applies these only when {@code llmId} is set.
     *       Setting them without {@code llmId} drops them silently server-side.</li>
     *   <li><b>Reranker-bound</b> ({@code relevanceThreshold}) — applied only when
     *       {@code rerankerId} is set.</li>
     *   <li><b>Pipeline-bound</b> ({@code maxResults}, {@code chronologicalResort}) —
     *       applied when the post-processor pipeline runs at all, which requires
     *       at least one activating key ({@code llmId} or {@code rerankerId}).</li>
     * </ul>
     *
     * <p>Falls under {@code inv-sdk-convenience-validates-at-earliest}.
     */
    private static void validateOrphans(
        String llmId,
        String rerankerId,
        Double llmTemp,
        Long genTokenBudget,
        Double relevanceThreshold,
        Long maxResults,
        String prompt,
        String sysPrompt,
        Boolean chronologicalResort
    ) {
        java.util.List<String> llmOrphans = new java.util.ArrayList<>();
        if (llmId == null) {
            if (llmTemp != null) llmOrphans.add("llmTemp");
            if (genTokenBudget != null) llmOrphans.add("genTokenBudget");
            if (prompt != null) llmOrphans.add("prompt");
            if (sysPrompt != null) llmOrphans.add("sysPrompt");
        }
        java.util.List<String> rerankerOrphans = new java.util.ArrayList<>();
        if (rerankerId == null) {
            if (relevanceThreshold != null) rerankerOrphans.add("relevanceThreshold");
        }
        java.util.List<String> pipelineOrphans = new java.util.ArrayList<>();
        if (llmId == null && rerankerId == null) {
            if (maxResults != null) pipelineOrphans.add("maxResults");
            if (chronologicalResort != null) pipelineOrphans.add("chronologicalResort");
        }
        if (llmOrphans.isEmpty() && rerankerOrphans.isEmpty() && pipelineOrphans.isEmpty()) {
            return;
        }
        StringBuilder msg = new StringBuilder("ChatPostProcessorConfig: ");
        boolean first = true;
        if (!llmOrphans.isEmpty()) {
            msg.append(llmOrphans).append(" require llmId to be set");
            first = false;
        }
        if (!rerankerOrphans.isEmpty()) {
            if (!first) msg.append("; ");
            msg.append(rerankerOrphans).append(" require rerankerId to be set");
            first = false;
        }
        if (!pipelineOrphans.isEmpty()) {
            if (!first) msg.append("; ");
            msg.append(pipelineOrphans)
               .append(" require at least one of llmId or rerankerId to be set");
        }
        msg.append(". Without the matching activating key these post-processor ")
           .append("parameters are silently dropped server-side. Either set the ")
           .append("activating key or remove the orphan parameter(s).");
        throw new IllegalArgumentException(msg.toString());
    }

    public static final String FACTORY =
        "com.goodmem.retrieval.postprocess.ChatPostProcessorFactory";

    public static Builder builder() {
        return new Builder();
    }

    /** Materialise as a {@link PostProcessor} ready to drop into
     *  {@code RetrieveMemoryRequest.postProcessor(...)}. */
    public PostProcessor toPostProcessor() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (llmId != null) m.put("llm_id", llmId);
        if (rerankerId != null) m.put("reranker_id", rerankerId);
        if (llmTemp != null) m.put("llm_temp", llmTemp);
        if (genTokenBudget != null) m.put("gen_token_budget", genTokenBudget);
        if (relevanceThreshold != null) m.put("relevance_threshold", relevanceThreshold);
        if (maxResults != null) m.put("max_results", maxResults);
        if (prompt != null) m.put("prompt", prompt);
        if (sysPrompt != null) m.put("sys_prompt", sysPrompt);
        if (chronologicalResort != null) m.put("chronological_resort", chronologicalResort);
        return new PostProcessor(FACTORY, m);
    }

    public static final class Builder {
        private String llmId;
        private String rerankerId;
        private Double llmTemp;
        private Long genTokenBudget;
        private Double relevanceThreshold;
        private Long maxResults;
        private String prompt;
        private String sysPrompt;
        private Boolean chronologicalResort;

        /** LLM identifier wired to the chat factory. Required for any LLM-bound
         *  field ({@code llmTemp}, {@code genTokenBudget}, {@code prompt},
         *  {@code sysPrompt}). Pass {@code null} to clear. */
        public Builder llmId(String value) { this.llmId = value; return this; }
        /** UUID overload of {@link #llmId(String)}; converts via {@code toString()}. */
        public Builder llmId(java.util.UUID value) {
            this.llmId = value == null ? null : value.toString();
            return this;
        }
        /** Typed-handle overload of {@link #llmId(String)}; converts via {@code toString()}. */
        public Builder llmId(LlmId value) {
            this.llmId = value == null ? null : value.toString();
            return this;
        }

        /** Reranker identifier. Required when {@code relevanceThreshold} is set.
         *  Pass {@code null} to disable reranking. */
        public Builder rerankerId(String value) { this.rerankerId = value; return this; }
        /** UUID overload of {@link #rerankerId(String)}; converts via {@code toString()}. */
        public Builder rerankerId(java.util.UUID value) {
            this.rerankerId = value == null ? null : value.toString();
            return this;
        }
        /** Typed-handle overload of {@link #rerankerId(String)}. */
        public Builder rerankerId(RerankerId value) {
            this.rerankerId = value == null ? null : value.toString();
            return this;
        }

        /** Sampling temperature for the LLM, in {@code [0.0, 2.0]}. Lower
         *  produces more deterministic output, higher more diverse. Throws
         *  {@link IllegalArgumentException} on out-of-range; pass {@code null}
         *  to clear. Requires {@link #llmId(String)}. */
        public Builder llmTemp(Double value) {
            if (value != null && (value < 0.0 || value > 2.0)) throw new IllegalArgumentException(
                "llmTemp must be in [0.0, 2.0], got " + value);
            this.llmTemp = value;
            return this;
        }

        /** Maximum tokens the LLM may generate per response, {@code >= 1}.
         *  Throws {@link IllegalArgumentException} on non-positive; pass
         *  {@code null} to clear. Requires {@link #llmId(String)}. */
        public Builder genTokenBudget(Long value) {
            if (value != null && value < 1L) throw new IllegalArgumentException(
                "genTokenBudget must be >= 1, got " + value);
            this.genTokenBudget = value;
            return this;
        }

        /** Minimum relevance score (in {@code [0.0, 1.0]}) a chunk must carry
         *  to remain in the context window after reranking. Throws
         *  {@link IllegalArgumentException} on out-of-range; pass {@code null}
         *  to clear. Requires {@link #rerankerId(String)}. */
        public Builder relevanceThreshold(Double value) {
            if (value != null && (value < 0.0 || value > 1.0)) throw new IllegalArgumentException(
                "relevanceThreshold must be in [0.0, 1.0], got " + value);
            this.relevanceThreshold = value;
            return this;
        }

        /** Hard cap on the number of chunks fed into the LLM, {@code >= 1}.
         *  Throws {@link IllegalArgumentException} on non-positive; pass
         *  {@code null} to clear. Requires {@link #llmId(String)} or
         *  {@link #rerankerId(String)}. */
        public Builder maxResults(Long value) {
            if (value != null && value < 1L) throw new IllegalArgumentException(
                "maxResults must be >= 1, got " + value);
            this.maxResults = value;
            return this;
        }

        /** User prompt template. Requires {@link #llmId(String)}. */
        public Builder prompt(String value) { this.prompt = value; return this; }
        /** System prompt template. Requires {@link #llmId(String)}. */
        public Builder sysPrompt(String value) { this.sysPrompt = value; return this; }
        /** Re-sort the post-rerank chunks chronologically (by memory creation
         *  time) before feeding the LLM. Helpful when you care about temporal
         *  order in the generated answer. Requires {@link #llmId(String)} or
         *  {@link #rerankerId(String)}. */
        public Builder chronologicalResort(Boolean value) {
            this.chronologicalResort = value;
            return this;
        }

        public ChatPostProcessorConfig build() {
            // The compact ctor delegates to validateOrphans; constructing the
            // record here therefore runs the same categorized check. Builder
            // and direct ctor produce identical diagnostics.
            return new ChatPostProcessorConfig(
                llmId, rerankerId, llmTemp, genTokenBudget,
                relevanceThreshold, maxResults, prompt, sysPrompt, chronologicalResort);
        }
    }
}
