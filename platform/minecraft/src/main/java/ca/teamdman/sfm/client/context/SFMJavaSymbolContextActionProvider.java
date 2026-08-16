package ca.teamdman.sfm.client.context;

import ca.teamdman.sfm.client.action.SFMFindReferencesAction;
import ca.teamdman.sfm.client.action.SFMJumpToDefinitionAction;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentPosition;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Java document contribution shared by Alt+Enter and editor right-click. */
public final class SFMJavaSymbolContextActionProvider implements SFMContextActionProvider {
    public static final String ID = "sfm:java-symbols";

    @Override
    public List<Offer> offers(Request request) {
        Optional<SFMContextDocumentProjection> document = request.focusedContribution()
                .map(SFMContextContribution::projection)
                .filter(SFMContextDocumentProjection.class::isInstance)
                .map(SFMContextDocumentProjection.class::cast);
        if (document.isEmpty() || !isJava(document.orElseThrow())) return List.of();
        Optional<SFMTextDocumentPosition> point = primaryTextPoint(document.orElseThrow());
        if (point.isEmpty() || !isJavaSymbolPoint(document.orElseThrow().currentText(), point.orElseThrow())) {
            return List.of();
        }
        return List.of(
                new Offer(0, SFMActionChoice.invoke(SFMJumpToDefinitionAction.ID, "")),
                new Offer(10, SFMActionChoice.invoke(SFMFindReferencesAction.ID, ""))
        );
    }

    private static boolean isJava(SFMContextDocumentProjection document) {
        return document.baseline().path()
                .map(path -> path.canonical().toLowerCase(Locale.ROOT).endsWith(".java"))
                .orElse(false);
    }

    private static Optional<SFMTextDocumentPosition> primaryTextPoint(SFMContextDocumentProjection document) {
        return document.cursors().stream()
                .filter(SFMContextCursorProjection::primary)
                .filter(SFMContextCursorProjection::active)
                .findFirst()
                .flatMap(cursor -> {
                    if (cursor.position() instanceof SFMContextPosition.Text text) {
                        return Optional.of(text.position());
                    }
                    if (cursor.position() instanceof SFMContextPosition.Canvas canvas) {
                        return canvas.textHit();
                    }
                    return Optional.empty();
                });
    }

    private static boolean isJavaSymbolPoint(String text, SFMTextDocumentPosition position) {
        int utf16 = SFMContextTextCoordinates.utf16OffsetAtUtf8Byte(text, position.byteOffset());
        int candidate = -1;
        if (utf16 < text.length() && isJavaSymbolCodePoint(text.codePointAt(utf16))) {
            candidate = utf16;
        } else if (utf16 > 0) {
            int previous = text.offsetByCodePoints(utf16, -1);
            if (isJavaSymbolCodePoint(text.codePointAt(previous))) candidate = previous;
        }
        return candidate >= 0 && isJavaCodeAt(text, candidate);
    }

    private static boolean isJavaSymbolCodePoint(int codePoint) {
        return Character.isJavaIdentifierPart(codePoint) || codePoint == '@';
    }

    /** Deterministic local lexer sufficient to distinguish Java code from literal/comment bodies. */
    private static boolean isJavaCodeAt(String text, int target) {
        JavaLexicalState state = JavaLexicalState.CODE;
        for (int index = 0; index < text.length();) {
            if (index == target) return state == JavaLexicalState.CODE;
            char value = text.charAt(index);
            switch (state) {
                case CODE -> {
                    if (startsWith(text, index, "//")) {
                        state = JavaLexicalState.LINE_COMMENT;
                        index += 2;
                    } else if (startsWith(text, index, "/*")) {
                        state = JavaLexicalState.BLOCK_COMMENT;
                        index += 2;
                    } else if (startsWith(text, index, "\"\"\"")) {
                        state = JavaLexicalState.TEXT_BLOCK;
                        index += 3;
                    } else if (value == '"') {
                        state = JavaLexicalState.STRING;
                        index++;
                    } else if (value == '\'') {
                        state = JavaLexicalState.CHARACTER;
                        index++;
                    } else {
                        index++;
                    }
                }
                case LINE_COMMENT -> {
                    if (value == '\r' || value == '\n') state = JavaLexicalState.CODE;
                    index++;
                }
                case BLOCK_COMMENT -> {
                    if (startsWith(text, index, "*/")) {
                        state = JavaLexicalState.CODE;
                        index += 2;
                    } else {
                        index++;
                    }
                }
                case STRING -> {
                    if (value == '\\') {
                        if (target == index + 1) return false;
                        index = Math.min(text.length(), index + 2);
                    } else {
                        if (value == '"') state = JavaLexicalState.CODE;
                        index++;
                    }
                }
                case CHARACTER -> {
                    if (value == '\\') {
                        if (target == index + 1) return false;
                        index = Math.min(text.length(), index + 2);
                    } else {
                        if (value == '\'') state = JavaLexicalState.CODE;
                        index++;
                    }
                }
                case TEXT_BLOCK -> {
                    if (value == '\\') {
                        if (target == index + 1) return false;
                        index = Math.min(text.length(), index + 2);
                    } else if (startsWith(text, index, "\"\"\"")) {
                        state = JavaLexicalState.CODE;
                        index += 3;
                    } else {
                        index++;
                    }
                }
            }
        }
        return target == text.length() && state == JavaLexicalState.CODE;
    }

    private static boolean startsWith(String text, int index, String token) {
        return index + token.length() <= text.length() && text.regionMatches(index, token, 0, token.length());
    }

    private enum JavaLexicalState {
        CODE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        STRING,
        CHARACTER,
        TEXT_BLOCK
    }
}
