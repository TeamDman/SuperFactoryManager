package ca.teamdman.sfm.client.text_editor;

/** Zero-based Unicode-scalar line/column plus exact UTF-8 byte boundary. */
public record SFMTextDocumentPosition(int line, int column, int byteOffset) {
    public SFMTextDocumentPosition {
        if (line < 0 || column < 0 || byteOffset < 0) {
            throw new IllegalArgumentException("Text positions must not be negative");
        }
    }
}
