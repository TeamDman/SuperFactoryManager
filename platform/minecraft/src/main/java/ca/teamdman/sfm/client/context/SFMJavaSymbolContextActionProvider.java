package ca.teamdman.sfm.client.context;

import ca.teamdman.sfm.client.action.SFMFindReferencesAction;
import ca.teamdman.sfm.client.action.SFMJumpToDefinitionAction;
import ca.teamdman.sfm.client.action.SFMSymbolCopyAction;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.symbol.SFMSymbolInspectionEvidenceSource;
import ca.teamdman.sfm.client.symbol.SFMSymbolInspectionFormatters;
import ca.teamdman.sfm.client.symbol.SFMSymbolInspectionSessions;
import ca.teamdman.sfm.client.symbol.SFMSymbolInspectionSnapshot;
import ca.teamdman.sfm.client.symbol.SFMSymbolNavigationRuntime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Java document contribution shared by Alt+Enter and editor right-click. */
public final class SFMJavaSymbolContextActionProvider implements SFMContextActionProvider {
    public static final String ID = "sfm:java-symbols";
    private final SFMSymbolInspectionSessions sessions;
    private final Supplier<Optional<String>> minecraftBranch;

    public SFMJavaSymbolContextActionProvider() {
        this(SFMSymbolInspectionSessions.shared(), SFMJavaSymbolContextActionProvider::currentMinecraftBranch);
    }

    SFMJavaSymbolContextActionProvider(
            SFMSymbolInspectionSessions sessions,
            String minecraftBranch
    ) {
        this(sessions, () -> Optional.of(requireBranch(minecraftBranch)));
    }

    private SFMJavaSymbolContextActionProvider(
            SFMSymbolInspectionSessions sessions,
            Supplier<Optional<String>> minecraftBranch
    ) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.minecraftBranch = Objects.requireNonNull(minecraftBranch, "minecraftBranch");
    }

    @Override
    public List<Offer> offers(Request request) {
        Optional<SFMContextDocumentProjection> document = request.focusedContribution()
                .map(SFMContextContribution::projection)
                .filter(SFMContextDocumentProjection.class::isInstance)
                .map(SFMContextDocumentProjection.class::cast);
        if (document.isEmpty() || !isJava(document.orElseThrow())) return List.of();
        SFMContextDocumentProjection capturedDocument = document.orElseThrow();
        Optional<SFMSymbolInspectionSnapshot.CapturedPoint> point =
                SFMSymbolInspectionSnapshot.capturePoint(capturedDocument);
        if (point.isEmpty()) return List.of();
        Optional<SFMSymbolInspectionSnapshot.SemanticEvidence> evidence = semanticEvidence(
                request, capturedDocument, point.orElseThrow());
        Optional<SFMSymbolInspectionSnapshot> captured = SFMSymbolInspectionSnapshot.capture(
                request,
                evidence,
                Objects.requireNonNull(minecraftBranch.get(), "minecraftBranch result")
        );
        if (captured.isEmpty()) return List.of();
        SFMSymbolInspectionSessions.Session session = sessions.capture(captured.orElseThrow());

        ArrayList<Offer> offers = new ArrayList<>();
        boolean lexicalSymbol = isJavaSymbolPoint(capturedDocument.currentText(), point.orElseThrow());
        if (lexicalSymbol || !captured.orElseThrow().definitionOutlinks().isEmpty()) {
            offers.add(new Offer(0, SFMActionChoice.invoke(SFMJumpToDefinitionAction.ID, "")));
        }
        if (lexicalSymbol || !captured.orElseThrow().referenceOutlinks().isEmpty()) {
            offers.add(new Offer(10, SFMActionChoice.invoke(SFMFindReferencesAction.ID, "")));
        }
        int rank = 20;
        for (SFMSymbolInspectionFormatters.Projection projection : SFMSymbolInspectionFormatters.Projection.values()) {
            offers.add(new Offer(
                    rank++,
                    SFMActionChoice.invoke(SFMSymbolCopyAction.id(projection), Long.toString(session.id()))
            ));
        }
        return List.copyOf(offers);
    }

    private static Optional<String> currentMinecraftBranch() {
        String configured = System.getProperty(SFMSymbolNavigationRuntime.BRANCH_PROPERTY, "").strip();
        return configured.isEmpty() ? Optional.empty() : Optional.of(configured);
    }

    private static String requireBranch(String value) {
        String branch = Objects.requireNonNull(value, "minecraftBranch").strip();
        if (branch.isEmpty()) throw new IllegalArgumentException("minecraftBranch must not be blank");
        return branch;
    }

    private static Optional<SFMSymbolInspectionSnapshot.SemanticEvidence> semanticEvidence(
            Request request,
            SFMContextDocumentProjection document,
            SFMSymbolInspectionSnapshot.CapturedPoint point
    ) {
        if (!(request.actionContext().originatingHost() instanceof SFMScreenMultiplexer workspace)
                || request.actionContext().originatingPanelId() == null
                || !(workspace.panelInstance(request.actionContext().originatingPanelId())
                instanceof SFMSymbolInspectionEvidenceSource source)) {
            return Optional.empty();
        }
        return source.captureSymbolInspectionEvidence(document, point);
    }

    private static boolean isJava(SFMContextDocumentProjection document) {
        return document.baseline().path()
                .map(path -> path.canonical().toLowerCase(Locale.ROOT).endsWith(".java"))
                .orElse(false);
    }

    private static boolean isJavaSymbolPoint(
            String text,
            SFMSymbolInspectionSnapshot.CapturedPoint point
    ) {
        if (!point.localKind().equals("java-identifier")) return false;
        int candidate = SFMContextTextCoordinates.utf16OffsetAtUtf8Byte(
                text,
                point.localRange().start().byteOffset()
        );
        if (candidate >= text.length()) return false;
        return isJavaCodeAt(text, candidate);
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
