package ai.pairsys.goodmem.client.errors;

/**
 * HTTP 422 Unprocessable Entity — the request was syntactically
 * well-formed but failed semantic validation (e.g. a temperature
 * outside the LLM's accepted range, an embedder model identifier the
 * provider doesn't recognize). Distinct from
 * {@link BadRequestException} (HTTP 400), which signals shape errors
 * the SDK should ideally have caught client-side.
 */
public class UnprocessableEntityException extends ApiException {
    public UnprocessableEntityException(String message, int statusCode, String body) {
        super(message, statusCode, body);
    }
}
