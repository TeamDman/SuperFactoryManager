package ca.teamdman.sfm.client.semantic;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic lexical interaction regions for Java canvas text.
 *
 * <p>This is deliberately not a nearest-token fallback: every non-whitespace
 * scalar belongs to exactly one explicit half-open region.  Rust semantic maps
 * may enrich these regions with outlinks, while malformed/incomplete source
 * remains safely addressable in the editor.</p>
 */
public final class SFMJavaCanvasInteractionRegions {
    private static final Set<String> KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false",
            "null", "record", "sealed", "permits", "non-sealed", "var", "yield"
    );
    private static final List<String> OPERATORS = List.of(
            ">>>=", "<<=", ">>=", "...", "::", "->", "++", "--", "==", "!=", "<=", ">=",
            "&&", "||", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<", ">>>", ">>",
            "+", "-", "*", "/", "%", "&", "|", "^", "!", "~", "=", "<", ">"
    );

    private SFMJavaCanvasInteractionRegions() {
    }

    public enum Kind {
        IDENTIFIER,
        KEYWORD,
        NUMBER_LITERAL,
        STRING_LITERAL,
        CHARACTER_LITERAL,
        LINE_COMMENT,
        BLOCK_COMMENT,
        ANNOTATION_MARKER,
        DELIMITER,
        SEPARATOR,
        OPERATOR,
        OTHER
    }

    public record Region(int utf16Start, int utf16End, int navigationUtf16Offset, Kind kind) {
        public Region {
            if (utf16Start < 0 || utf16End <= utf16Start) {
                throw new IllegalArgumentException("interaction region must be non-empty and ordered");
            }
            if (navigationUtf16Offset < 0) {
                throw new IllegalArgumentException("navigation offset must be non-negative");
            }
            Objects.requireNonNull(kind, "kind");
        }

        public boolean contains(int utf16Offset) {
            return utf16Offset >= utf16Start && utf16Offset < utf16End;
        }
    }

    public record Index(String text, List<Region> regions) {
        public Index {
            Objects.requireNonNull(text, "text");
            regions = List.copyOf(Objects.requireNonNull(regions, "regions"));
            int previousEnd = 0;
            for (Region region : regions) {
                if (region.utf16Start() < previousEnd || region.utf16End() > text.length()
                        || region.navigationUtf16Offset() > text.length()) {
                    throw new IllegalArgumentException("interaction regions must be ordered and inside the document");
                }
                previousEnd = region.utf16End();
            }
        }

        public Optional<Region> atUtf16(int offset) {
            if (offset < 0 || offset >= text.length()) return Optional.empty();
            int low = 0;
            int high = regions.size();
            while (low < high) {
                int middle = (low + high) >>> 1;
                Region region = regions.get(middle);
                if (offset < region.utf16Start()) {
                    high = middle;
                } else if (offset >= region.utf16End()) {
                    low = middle + 1;
                } else {
                    return Optional.of(region);
                }
            }
            return Optional.empty();
        }
    }

    public static Index index(String text) {
        Objects.requireNonNull(text, "text");
        ArrayList<Region> regions = new ArrayList<>();
        int cursor = 0;
        while (cursor < text.length()) {
            int codePoint = text.codePointAt(cursor);
            if (Character.isWhitespace(codePoint)) {
                cursor += Character.charCount(codePoint);
                continue;
            }
            int start = cursor;
            if (startsWith(text, cursor, "//")) {
                cursor = scanLineComment(text, cursor + 2);
                regions.add(region(start, cursor, start, Kind.LINE_COMMENT));
                continue;
            }
            if (startsWith(text, cursor, "/*")) {
                cursor = scanBlockComment(text, cursor + 2);
                regions.add(region(start, cursor, start, Kind.BLOCK_COMMENT));
                continue;
            }
            if (startsWith(text, cursor, "\"\"\"")) {
                cursor = scanTextBlock(text, cursor + 3);
                regions.add(region(start, cursor, start, Kind.STRING_LITERAL));
                continue;
            }
            if (codePoint == '"' || codePoint == '\'') {
                cursor = scanQuoted(text, cursor + 1, codePoint);
                regions.add(region(start, cursor, start,
                        codePoint == '"' ? Kind.STRING_LITERAL : Kind.CHARACTER_LITERAL));
                continue;
            }
            if (codePoint == '@') {
                int next = cursor + 1;
                int navigation = next < text.length() && Character.isJavaIdentifierStart(text.codePointAt(next))
                        ? next
                        : start;
                cursor = next;
                regions.add(region(start, cursor, navigation, Kind.ANNOTATION_MARKER));
                continue;
            }
            if (Character.isJavaIdentifierStart(codePoint)) {
                cursor += Character.charCount(codePoint);
                while (cursor < text.length()) {
                    int value = text.codePointAt(cursor);
                    if (!Character.isJavaIdentifierPart(value)) break;
                    cursor += Character.charCount(value);
                }
                String identifier = text.substring(start, cursor);
                regions.add(region(start, cursor, start, KEYWORDS.contains(identifier) ? Kind.KEYWORD : Kind.IDENTIFIER));
                continue;
            }
            if (Character.isDigit(codePoint) || (codePoint == '.' && startsNumberAfterDot(text, cursor))) {
                cursor = scanNumber(text, cursor);
                regions.add(region(start, cursor, start, Kind.NUMBER_LITERAL));
                continue;
            }
            String operator = operatorAt(text, cursor);
            if (operator != null) {
                cursor += operator.length();
                regions.add(region(start, cursor, start, Kind.OPERATOR));
                continue;
            }
            cursor += Character.charCount(codePoint);
            Kind kind = switch (codePoint) {
                case '(', ')', '[', ']', '{', '}' -> Kind.DELIMITER;
                case ';', ',', '.', ':', '?' -> Kind.SEPARATOR;
                default -> Kind.OTHER;
            };
            regions.add(region(start, cursor, start, kind));
        }
        return new Index(text, regions);
    }

    private static Region region(int start, int end, int navigation, Kind kind) {
        return new Region(start, end, navigation, kind);
    }

    private static int scanLineComment(String text, int cursor) {
        while (cursor < text.length()) {
            int value = text.codePointAt(cursor);
            if (value == '\r' || value == '\n') break;
            cursor += Character.charCount(value);
        }
        return cursor;
    }

    private static int scanBlockComment(String text, int cursor) {
        int close = text.indexOf("*/", cursor);
        return close < 0 ? text.length() : close + 2;
    }

    private static int scanTextBlock(String text, int cursor) {
        while (cursor < text.length()) {
            if (startsWith(text, cursor, "\"\"\"") && !escaped(text, cursor)) return cursor + 3;
            cursor += Character.charCount(text.codePointAt(cursor));
        }
        return text.length();
    }

    private static int scanQuoted(String text, int cursor, int quote) {
        while (cursor < text.length()) {
            int value = text.codePointAt(cursor);
            cursor += Character.charCount(value);
            if (value == quote && !escaped(text, cursor - Character.charCount(value))) return cursor;
            if (value == '\r' || value == '\n') return cursor;
        }
        return text.length();
    }

    private static boolean escaped(String text, int offset) {
        int slashes = 0;
        for (int index = offset - 1; index >= 0 && text.charAt(index) == '\\'; index--) slashes++;
        return (slashes & 1) != 0;
    }

    private static int scanNumber(String text, int cursor) {
        while (cursor < text.length()) {
            int value = text.codePointAt(cursor);
            if (Character.isLetterOrDigit(value) || value == '_' || value == '.') {
                cursor += Character.charCount(value);
                continue;
            }
            if ((value == '+' || value == '-') && cursor > 0) {
                int previous = text.codePointBefore(cursor);
                if (previous == 'e' || previous == 'E' || previous == 'p' || previous == 'P') {
                    cursor += 1;
                    continue;
                }
            }
            break;
        }
        return cursor;
    }

    private static boolean startsNumberAfterDot(String text, int cursor) {
        int next = cursor + 1;
        return next < text.length() && Character.isDigit(text.codePointAt(next));
    }

    private static String operatorAt(String text, int cursor) {
        for (String operator : OPERATORS) if (startsWith(text, cursor, operator)) return operator;
        return null;
    }

    private static boolean startsWith(String text, int offset, String value) {
        return offset >= 0 && offset + value.length() <= text.length() && text.startsWith(value, offset);
    }
}
