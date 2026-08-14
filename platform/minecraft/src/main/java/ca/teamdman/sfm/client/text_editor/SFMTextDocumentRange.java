package ca.teamdman.sfm.client.text_editor;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Immutable half-open source range with row/column and UTF-8 byte witnesses. */
public record SFMTextDocumentRange(
        SFMTextDocumentPosition start,
        SFMTextDocumentPosition end
) {
    public SFMTextDocumentRange {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (start.byteOffset() > end.byteOffset()) {
            throw new IllegalArgumentException("Text range start must not follow its end");
        }
    }

    /** Rejects stale, non-boundary, or line-ending-inconsistent coordinates. */
    public void validateAgainst(String text) {
        Objects.requireNonNull(text, "text");
        SFMTextDocumentPosition actualStart = positionAtByteOffset(text, start.byteOffset());
        SFMTextDocumentPosition actualEnd = positionAtByteOffset(text, end.byteOffset());
        if (!start.equals(actualStart)) {
            throw new IllegalArgumentException("Text range start does not match the UTF-8 document: expected "
                    + actualStart + " but received " + start);
        }
        if (!end.equals(actualEnd)) {
            throw new IllegalArgumentException("Text range end does not match the UTF-8 document: expected "
                    + actualEnd + " but received " + end);
        }
    }

    public static SFMTextDocumentPosition positionAtByteOffset(String text, int requestedByteOffset) {
        Objects.requireNonNull(text, "text");
        int totalBytes = text.getBytes(StandardCharsets.UTF_8).length;
        if (requestedByteOffset < 0 || requestedByteOffset > totalBytes) {
            throw new IllegalArgumentException(
                    "UTF-8 byte offset " + requestedByteOffset + " is outside 0.." + totalBytes
            );
        }
        int line = 0;
        int column = 0;
        int bytes = 0;
        for (int index = 0; index < text.length();) {
            if (bytes == requestedByteOffset) {
                return new SFMTextDocumentPosition(line, column, bytes);
            }
            int codePoint = text.codePointAt(index);
            int charCount = Character.charCount(codePoint);
            int codePointBytes = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            if (requestedByteOffset > bytes && requestedByteOffset < bytes + codePointBytes) {
                throw new IllegalArgumentException("UTF-8 byte offset splits a Unicode scalar: " + requestedByteOffset);
            }
            bytes += codePointBytes;
            if (codePoint == '\r') {
                if (index + charCount < text.length() && text.codePointAt(index + charCount) == '\n') {
                    if (bytes == requestedByteOffset) {
                        // The interior CR/LF boundary is a byte boundary but is
                        // not a stable text coordinate in the CRLF model.
                        throw new IllegalArgumentException("UTF-8 byte offset splits a CRLF line ending");
                    }
                    bytes += 1;
                    index += 1;
                }
                line++;
                column = 0;
            } else if (codePoint == '\n') {
                line++;
                column = 0;
            } else {
                column++;
            }
            index += charCount;
        }
        if (bytes == requestedByteOffset) return new SFMTextDocumentPosition(line, column, bytes);
        throw new IllegalArgumentException("UTF-8 byte offset is not a document boundary: " + requestedByteOffset);
    }
}
