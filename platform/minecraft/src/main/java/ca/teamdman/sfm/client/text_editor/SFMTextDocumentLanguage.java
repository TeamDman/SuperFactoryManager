package ca.teamdman.sfm.client.text_editor;

import ca.teamdman.sfm.client.explorer.SFMPath;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Explicit source-language metadata carried with an editor document.
 *
 * <p>The renderer consumes the highlighting route recorded here; it must not
 * infer a language from a panel title. Unknown languages remain visible as
 * neutral text until a syntax provider is registered for them.</p>
 */
public record SFMTextDocumentLanguage(String id, Highlighting highlighting) {
    public static final int MAXIMUM_ID_UTF8_BYTES = 64;

    public enum Highlighting {
        LOCAL_SFML,
        LOCAL_LEXICAL,
        REMOTE_WORKER,
        NEUTRAL
    }

    public SFMTextDocumentLanguage {
        id = Objects.requireNonNull(id, "id").strip().toLowerCase(Locale.ROOT);
        Objects.requireNonNull(highlighting, "highlighting");
        if (id.isEmpty()) throw new IllegalArgumentException("Document language id must not be blank");
        if (id.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_ID_UTF8_BYTES) {
            throw new IllegalArgumentException("Document language id exceeds the UTF-8 byte limit");
        }
        if (!id.matches("[a-z][a-z0-9+._-]*")) {
            throw new IllegalArgumentException("Document language id is not canonical: " + id);
        }
    }

    public static SFMTextDocumentLanguage declared(String languageId) {
        String normalized = Objects.requireNonNull(languageId, "languageId")
                .strip()
                .toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return plainText();
        return switch (normalized) {
            case "sfml" -> sfml();
            case "java" -> java();
            case "diff", "patch" -> diff();
            case "json", "json5" -> json();
            case "rs", "rust" -> remote("rust");
            case "gradle", "groovy" -> remote("groovy");
            case "md", "markdown" -> remote("markdown");
            case "ps1", "powershell" -> remote("powershell");
            case "ts", "typescript" -> remote("typescript");
            case "toml" -> remote("toml");
            case "properties", "cfg", "ini" ->
                    new SFMTextDocumentLanguage(normalized, Highlighting.LOCAL_LEXICAL);
            case "plain", "plaintext", "text", "txt" -> plainText();
            default -> new SFMTextDocumentLanguage(normalized, Highlighting.NEUTRAL);
        };
    }

    public static SFMTextDocumentLanguage fromPath(SFMPath path) {
        Objects.requireNonNull(path, "path");
        if (path.segments().isEmpty()) return plainText();
        return fromFileName(path.segments().get(path.segments().size() - 1));
    }

    public static SFMTextDocumentLanguage fromFileName(String fileName) {
        String normalized = Objects.requireNonNull(fileName, "fileName")
                .replace('\\', '/')
                .toLowerCase(Locale.ROOT);
        int separator = normalized.lastIndexOf('/');
        if (separator >= 0) normalized = normalized.substring(separator + 1);
        if (normalized.endsWith(".sfm-review.json")) return json();
        int dot = normalized.lastIndexOf('.');
        if (dot <= 0 || dot == normalized.length() - 1) return plainText();
        String extension = normalized.substring(dot + 1);
        return switch (extension) {
            case "md", "markdown" -> declared("markdown");
            case "rs" -> declared("rust");
            default -> declared(extension);
        };
    }

    public static SFMTextDocumentLanguage sfml() {
        return new SFMTextDocumentLanguage("sfml", Highlighting.LOCAL_SFML);
    }

    public static SFMTextDocumentLanguage java() {
        return new SFMTextDocumentLanguage("java", Highlighting.REMOTE_WORKER);
    }

    public static SFMTextDocumentLanguage diff() {
        return new SFMTextDocumentLanguage("diff", Highlighting.NEUTRAL);
    }

    public static SFMTextDocumentLanguage json() {
        return remote("json");
    }

    private static SFMTextDocumentLanguage remote(String id) {
        return new SFMTextDocumentLanguage(id, Highlighting.REMOTE_WORKER);
    }

    public static SFMTextDocumentLanguage plainText() {
        return new SFMTextDocumentLanguage("text", Highlighting.NEUTRAL);
    }

    public boolean usesLocalSfmlHighlighting() {
        return highlighting == Highlighting.LOCAL_SFML;
    }

    public Optional<String> remoteWorkerLanguage() {
        return highlighting == Highlighting.REMOTE_WORKER ? Optional.of(id) : Optional.empty();
    }
}
