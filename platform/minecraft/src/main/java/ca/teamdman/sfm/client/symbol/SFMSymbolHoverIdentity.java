package ca.teamdman.sfm.client.symbol;

import java.util.Objects;

/**
 * Immutable identity for one symbol-hover observation.
 *
 * <p>The identity deliberately includes every input which can make an asynchronous definition
 * availability answer stale. Rendering code only consumes a state-machine snapshot; it does not
 * create identities or start lookups.</p>
 */
public record SFMSymbolHoverIdentity(
        EditorOrigin editorOrigin,
        DocumentVersion document,
        TextGlyphRange range,
        Modifiers modifiers
) {
    public SFMSymbolHoverIdentity {
        Objects.requireNonNull(editorOrigin, "editorOrigin");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(modifiers, "modifiers");
    }

    public boolean requestsDefinitionNavigation() {
        return modifiers.requestsDefinitionNavigation();
    }

    /** Stable editor ownership, independent of mutable screen object identity. */
    public record EditorOrigin(
            String screenId,
            String workspaceId,
            String stackId,
            String panelId,
            String editorId,
            long focusGeneration
    ) {
        public EditorOrigin {
            requireNonBlank(screenId, "screenId");
            requireNonBlank(workspaceId, "workspaceId");
            requireNonBlank(stackId, "stackId");
            requireNonBlank(panelId, "panelId");
            requireNonBlank(editorId, "editorId");
            requireNonNegative(focusGeneration, "focusGeneration");
        }
    }

    /** Content identity is both hash- and generation-sensitive. */
    public record DocumentVersion(String address, String contentHash, long generation) {
        public DocumentVersion {
            requireNonBlank(address, "address");
            requireNonBlank(contentHash, "contentHash");
            requireNonNegative(generation, "generation");
        }
    }

    /**
     * The same half-open range represented in UTF-16, Unicode-scalar, UTF-8, and shaped-glyph
     * coordinates. Glyph coordinates are supplied by the editor because shaping is font-specific.
     */
    public record TextGlyphRange(
            int utf16Start,
            int utf16End,
            int scalarStart,
            int scalarEnd,
            long utf8Start,
            long utf8End,
            int glyphStart,
            int glyphEnd
    ) {
        public TextGlyphRange {
            requireRange(utf16Start, utf16End, "UTF-16");
            requireRange(scalarStart, scalarEnd, "Unicode-scalar");
            requireRange(utf8Start, utf8End, "UTF-8");
            requireRange(glyphStart, glyphEnd, "glyph");
            if (utf16Start == utf16End || scalarStart == scalarEnd || utf8Start == utf8End
                    || glyphStart == glyphEnd) {
                throw new IllegalArgumentException("A symbol hover range must not be empty");
            }
        }

        public static TextGlyphRange fromUtf16(
                String documentText,
                int utf16Start,
                int utf16End,
                int glyphStart,
                int glyphEnd
        ) {
            Objects.requireNonNull(documentText, "documentText");
            if (utf16Start < 0 || utf16End < utf16Start || utf16End > documentText.length()) {
                throw new IllegalArgumentException("UTF-16 range lies outside the document");
            }
            requireCodePointBoundary(documentText, utf16Start, "utf16Start");
            requireCodePointBoundary(documentText, utf16End, "utf16End");
            int scalarOffset = 0;
            long utf8Offset = 0;
            int scalarStart = 0;
            int scalarEnd = 0;
            long utf8Start = 0;
            long utf8End = 0;
            for (int index = 0; index <= utf16End;) {
                if (index == utf16Start) {
                    scalarStart = scalarOffset;
                    utf8Start = utf8Offset;
                }
                if (index == utf16End) {
                    scalarEnd = scalarOffset;
                    utf8End = utf8Offset;
                    break;
                }
                int codePoint = documentText.codePointAt(index);
                index += Character.charCount(codePoint);
                scalarOffset++;
                utf8Offset += utf8Length(codePoint);
            }
            return new TextGlyphRange(
                    utf16Start,
                    utf16End,
                    scalarStart,
                    scalarEnd,
                    utf8Start,
                    utf8End,
                    glyphStart,
                    glyphEnd
            );
        }

        private static void requireCodePointBoundary(String text, int offset, String label) {
            if (offset > 0 && offset < text.length()
                    && Character.isHighSurrogate(text.charAt(offset - 1))
                    && Character.isLowSurrogate(text.charAt(offset))) {
                throw new IllegalArgumentException(label + " splits a surrogate pair");
            }
        }

        private static int utf8Length(int codePoint) {
            if (codePoint <= 0x7F) return 1;
            if (codePoint <= 0x7FF) return 2;
            if (codePoint <= 0xFFFF) return 3;
            return 4;
        }
    }

    public record Modifiers(boolean control, boolean alt, boolean shift, boolean superKey) {
        public static final Modifiers NONE = new Modifiers(false, false, false, false);

        public boolean requestsDefinitionNavigation() {
            return control && !alt;
        }

        public Modifiers withControl(boolean value) {
            return new Modifiers(value, alt, shift, superKey);
        }
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
    }

    private static void requireNonNegative(long value, String label) {
        if (value < 0) throw new IllegalArgumentException(label + " must not be negative");
    }

    private static void requireRange(long start, long end, String label) {
        if (start < 0 || end < start) throw new IllegalArgumentException(label + " range is invalid");
    }
}
