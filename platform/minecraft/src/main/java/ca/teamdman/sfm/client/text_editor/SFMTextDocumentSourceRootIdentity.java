package ca.teamdman.sfm.client.text_editor;

import java.util.Objects;

/**
 * Exact worker-negotiated source-root identity retained by an addressed document.
 *
 * <p>A physical source tree may intentionally back more than one semantic
 * source root.  The native path therefore cannot, by itself, identify the
 * source set and report namespace that subsequent symbol requests must use.</p>
 */
public record SFMTextDocumentSourceRootIdentity(
        String resolverId,
        String addressScheme,
        String rootId,
        String sourceSet,
        String reportPrefix
) {
    public SFMTextDocumentSourceRootIdentity {
        resolverId = nonBlank(resolverId, "resolverId");
        addressScheme = nonBlank(addressScheme, "addressScheme");
        rootId = nonBlank(rootId, "rootId");
        sourceSet = nonBlank(sourceSet, "sourceSet");
        reportPrefix = Objects.requireNonNull(reportPrefix, "reportPrefix");
        if (!resolverId.matches("[a-z][a-z0-9+.-]*")
                || !addressScheme.matches("[a-z][a-z0-9+.-]*")) {
            throw new IllegalArgumentException("Document source-root resolver identities must be canonical schemes");
        }
        if (reportPrefix.startsWith("/") || reportPrefix.endsWith("/") || reportPrefix.contains("\\")) {
            throw new IllegalArgumentException("Document source-root report prefix must be canonical and relative");
        }
        if (!reportPrefix.isEmpty()) {
            for (String segment : reportPrefix.split("/", -1)) {
                if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                    throw new IllegalArgumentException("Document source-root report prefix has an invalid segment");
                }
            }
        }
    }

    private static String nonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
