package ca.teamdman.sfm.client.context;

import ca.teamdman.sfm.client.text_editor.SFMTextDocumentPosition;
import java.util.Arrays;

/** One immutable source pass, then logarithmic exact endpoint lookup. CRLF has no interior address. */
public final class SFMTextCoordinateIndex {
    private final int[] utf16;
    private final int[] bytes;
    private final int[] lines;
    private final int[] columns;

    public SFMTextCoordinateIndex(String text) {
        int capacity = text.codePointCount(0, text.length()) + 1;
        int[] u = new int[capacity], b = new int[capacity], l = new int[capacity], c = new int[capacity];
        int count = 1, offset = 0, byteOffset = 0, line = 0, column = 0;
        while (offset < text.length()) {
            int point = text.codePointAt(offset);
            offset += Character.charCount(point);
            byteOffset += point <= 0x7f ? 1 : point <= 0x7ff ? 2 : point <= 0xffff ? 3 : 4;
            if (point == '\r' && offset < text.length() && text.charAt(offset) == '\n') { offset++; byteOffset++; }
            if (point == '\r' || point == '\n') { line++; column = 0; } else column++;
            u[count] = offset; b[count] = byteOffset; l[count] = line; c[count] = column; count++;
        }
        utf16 = Arrays.copyOf(u, count); bytes = Arrays.copyOf(b, count);
        lines = Arrays.copyOf(l, count); columns = Arrays.copyOf(c, count);
    }
    public SFMTextDocumentPosition atUtf16(int offset) {
        int i = Arrays.binarySearch(utf16, offset);
        if (i < 0) throw new IllegalArgumentException("UTF-16 offset is outside the source or splits a scalar/CRLF: " + offset);
        return position(i);
    }
    public int utf16(SFMTextDocumentPosition position) {
        if (position.byteOffset() > bytes[bytes.length - 1])
            throw new IllegalArgumentException("UTF-8 byte offset " + position.byteOffset() + " is outside 0.." + bytes[bytes.length - 1]);
        int i = Arrays.binarySearch(bytes, position.byteOffset());
        if (i < 0 || !position(i).equals(position)) throw new IllegalArgumentException("Position does not belong to this exact source");
        return utf16[i];
    }
    private SFMTextDocumentPosition position(int i) { return new SFMTextDocumentPosition(lines[i], columns[i], bytes[i]); }
}
