package ca.teamdman.sfm.client.history.document.runtime;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

/** Durable selector syntax for one exact document-history session or the focused session. */
public record SFMDocumentHistorySelector(Kind kind, Optional<String> sessionId) {
    public enum Kind {
        FOCUSED,
        EXACT
    }

    public SFMDocumentHistorySelector {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(sessionId, "sessionId");
        if (kind == Kind.FOCUSED && sessionId.isPresent()) {
            throw new IllegalArgumentException("A focused selector must not contain an exact session id");
        }
        if (kind == Kind.EXACT && sessionId.isEmpty()) {
            throw new IllegalArgumentException("An exact selector requires a session id");
        }
        sessionId = sessionId.map(value -> requireText(value, "sessionId"));
    }

    public static SFMDocumentHistorySelector focused() {
        return new SFMDocumentHistorySelector(Kind.FOCUSED, Optional.empty());
    }

    public static SFMDocumentHistorySelector exact(String sessionId) {
        return new SFMDocumentHistorySelector(Kind.EXACT, Optional.of(sessionId));
    }

    public static SFMDocumentHistorySelector parseCanonical(String value) {
        value = requireText(value, "selector");
        if (value.equals("focused")) return focused();
        if (!value.startsWith("id(") || !value.endsWith(")")) {
            throw new IllegalArgumentException("Expected focused or id(<percent-encoded-session-id>)");
        }
        String encoded = value.substring(3, value.length() - 1);
        if (encoded.isEmpty()) throw new IllegalArgumentException("Exact document selector id must not be empty");
        SFMDocumentHistorySelector parsed = exact(URLDecoder.decode(encoded, StandardCharsets.UTF_8));
        if (!parsed.canonical().equals(value)) {
            throw new IllegalArgumentException("Document selector must use canonical spelling: " + parsed.canonical());
        }
        return parsed;
    }

    public String canonical() {
        if (kind == Kind.FOCUSED) return "focused";
        String encoded = URLEncoder.encode(sessionId.orElseThrow(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return "id(" + encoded + ")";
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }
}
