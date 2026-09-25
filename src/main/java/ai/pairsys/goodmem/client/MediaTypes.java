// Hand-written — stable MIME type constants for common GoodMem upload paths.
package ai.pairsys.goodmem.client;

/**
 * MIME-type constants for fields that take a {@code contentType} string.
 * Use these over bare literals to catch typos at compile time:
 *
 * <pre>{@code
 * JsonMemoryCreationRequest req = JsonMemoryCreationRequest.builder()
 *     .spaceId(spaceId)
 *     .originalContent("hello")
 *     .contentType(MediaTypes.TEXT_PLAIN)
 *     .build();
 * }</pre>
 *
 * These are plain {@code String} constants so they drop in anywhere a
 * {@code String} contentType is expected — no wrapper type needed.
 */
public final class MediaTypes {
    private MediaTypes() {}

    public static final String TEXT_PLAIN = "text/plain";
    public static final String TEXT_MARKDOWN = "text/markdown";
    public static final String TEXT_HTML = "text/html";
    public static final String TEXT_CSV = "text/csv";

    public static final String APPLICATION_JSON = "application/json";
    public static final String APPLICATION_PDF = "application/pdf";
    public static final String APPLICATION_OCTET_STREAM = "application/octet-stream";
    public static final String APPLICATION_XML = "application/xml";
    public static final String APPLICATION_NDJSON = "application/x-ndjson";

    public static final String IMAGE_PNG = "image/png";
    public static final String IMAGE_JPEG = "image/jpeg";
    public static final String IMAGE_WEBP = "image/webp";
    public static final String IMAGE_TIFF = "image/tiff";

    public static final String AUDIO_MPEG = "audio/mpeg";
    public static final String AUDIO_WAV = "audio/wav";

    public static final String VIDEO_MP4 = "video/mp4";
}
