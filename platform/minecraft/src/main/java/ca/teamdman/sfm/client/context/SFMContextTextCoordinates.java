package ca.teamdman.sfm.client.context;

import ca.teamdman.sfm.client.text_editor.SFMTextDocumentPosition;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Exact conversions between editor coordinates and UTF-8 document witnesses. */
public final class SFMContextTextCoordinates {
    private SFMContextTextCoordinates() {
    }

    /**
     * Resolve a zero-based Unicode-scalar line and column.
     * CRLF is one logical line ending and has no addressable interior point.
     */
    public static SFMTextDocumentPosition atLineColumn(String text, int line, int column) {
        Objects.requireNonNull(text, "text");
        if (line < 0 || column < 0) {
            throw new IllegalArgumentException("Text line and column must not be negative");
        }
        List<Integer> starts = lineStarts(text);
        if (line >= starts.size()) {
            throw new IllegalArgumentException("Text line " + line + " is outside 0.." + (starts.size() - 1));
        }
        int start = starts.get(line);
        int physicalEnd = line + 1 < starts.size() ? starts.get(line + 1) : text.length();
        int logicalEnd = physicalEnd;
        if (logicalEnd > start && text.charAt(logicalEnd - 1) == '\n') logicalEnd--;
        if (logicalEnd > start && text.charAt(logicalEnd - 1) == '\r') logicalEnd--;
        int scalarCount = text.codePointCount(start, logicalEnd);
        if (column > scalarCount) {
            throw new IllegalArgumentException(
                    "Text column " + column + " is outside 0.." + scalarCount + " on line " + line
            );
        }
        int utf16Offset = text.offsetByCodePoints(start, column);
        return atUtf16Offset(text, utf16Offset);
    }

    /** Resolve a UTF-16 insertion offset without permitting split scalars or CRLF. */
    public static SFMTextDocumentPosition atUtf16Offset(String text, int requestedOffset) {
        Objects.requireNonNull(text, "text");
        if (requestedOffset < 0 || requestedOffset > text.length()) {
            throw new IllegalArgumentException(
                    "UTF-16 offset " + requestedOffset + " is outside 0.." + text.length()
            );
        }
        if (requestedOffset > 0
                && requestedOffset < text.length()
                && Character.isHighSurrogate(text.charAt(requestedOffset - 1))
                && Character.isLowSurrogate(text.charAt(requestedOffset))) {
            throw new IllegalArgumentException("UTF-16 offset splits a Unicode scalar");
        }
        if (requestedOffset > 0
                && requestedOffset < text.length()
                && text.charAt(requestedOffset - 1) == '\r'
                && text.charAt(requestedOffset) == '\n') {
            throw new IllegalArgumentException("UTF-16 offset splits a CRLF line ending");
        }

        int line = 0;
        int column = 0;
        int byteOffset = 0;
        for (int index = 0; index < requestedOffset;) {
            int codePoint = text.codePointAt(index);
            int charCount = Character.charCount(codePoint);
            if (index + charCount > requestedOffset) {
                throw new IllegalArgumentException("UTF-16 offset splits a Unicode scalar");
            }
            if (codePoint == '\r'
                    && index + charCount < text.length()
                    && text.codePointAt(index + charCount) == '\n') {
                if (index + charCount == requestedOffset) {
                    throw new IllegalArgumentException("UTF-16 offset splits a CRLF line ending");
                }
                byteOffset += 2;
                index += 2;
                line++;
                column = 0;
                continue;
            }
            byteOffset += utf8Length(codePoint);
            index += charCount;
            if (codePoint == '\r' || codePoint == '\n') {
                line++;
                column = 0;
            } else {
                column++;
            }
        }
        return new SFMTextDocumentPosition(line, column, byteOffset);
    }

    public static SFMTextDocumentRange rangeAtUtf16Offsets(String text, int start, int end) {
        if (start > end) throw new IllegalArgumentException("Text range start must not follow its end");
        return new SFMTextDocumentRange(atUtf16Offset(text, start), atUtf16Offset(text, end));
    }

    public static String sha256(String text) {
        Objects.requireNonNull(text, "text");
        return SFMContextHashes.sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    private static List<Integer> lineStarts(String text) {
        ArrayList<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int index = 0; index < text.length();) {
            int codePoint = text.codePointAt(index);
            int charCount = Character.charCount(codePoint);
            if (codePoint == '\r'
                    && index + charCount < text.length()
                    && text.codePointAt(index + charCount) == '\n') {
                index += 2;
                starts.add(index);
                continue;
            }
            index += charCount;
            if (codePoint == '\r' || codePoint == '\n') starts.add(index);
        }
        return List.copyOf(starts);
    }

    private static int utf8Length(int codePoint) {
        if (codePoint <= 0x7F) return 1;
        if (codePoint <= 0x7FF) return 2;
        if (codePoint <= 0xFFFF) return 3;
        return 4;
    }
}
