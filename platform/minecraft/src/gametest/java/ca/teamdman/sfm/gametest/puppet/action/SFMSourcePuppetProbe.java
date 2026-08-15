package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.context.SFMContextTextCoordinates;
import ca.teamdman.sfm.client.context.SFMContextDocumentProjection;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMResolverTextResult;
import ca.teamdman.sfm.client.screen.SFMDrawCanvasModel;
import ca.teamdman.sfm.client.screen.SFMDrawCanvasScreen;
import ca.teamdman.sfm.client.screen.SFMDrawCanvasSyntaxHighlightingHelper;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel;
import ca.teamdman.sfm.client.screen.text_editor.SFMDeferredTextEditorPanel;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextDocumentPanelState;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditorPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.syntax.SFMSyntaxHighlightRequest;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Test-only, source-content-free probes shared by the addressed-source puppets. */
final class SFMSourcePuppetProbe {
    static final long ONE_SECOND_MICROS = 1_000_000L;
    static final long MULTI_SECOND_MICROS = 2_000_000L;

    record EditorHandle(
            SFMWorkspacePanelId panelId,
            SFMTextDocumentPanelState state,
            Optional<SFMTextEditorPanel> resolvedPanel
    ) {
        EditorHandle {
            Objects.requireNonNull(panelId, "panelId");
            Objects.requireNonNull(state, "state");
            resolvedPanel = Objects.requireNonNull(resolvedPanel, "resolvedPanel");
        }
    }

    record ExplorerHandle(SFMWorkspacePanelId panelId, SFMExplorerPanel panel) {
        ExplorerHandle {
            Objects.requireNonNull(panelId, "panelId");
            Objects.requireNonNull(panel, "panel");
        }
    }

    private static final Field DEFERRED_DELEGATE = deferredDelegateField();
    private static final Field EDITOR_SCREEN = field(SFMTextEditorPanel.class, "screen");
    private static final Field DRAW_MODEL = field(SFMDrawCanvasScreen.class, "model");
    private static final Field CONTEXT_GENERATION = field(SFMDrawCanvasScreen.class, "contextGeneration");
    private static final Field DOCUMENT_GENERATION = field(SFMDrawCanvasScreen.class, "documentGeneration");
    private static final Field WORKSPACE_TOAST = field(SFMScreenMultiplexer.class, "workspaceToast");

    private SFMSourcePuppetProbe() {
    }

    static Optional<EditorHandle> editor(SFMScreenMultiplexer workspace, Path file) {
        Objects.requireNonNull(workspace, "workspace");
        SFMPath expected = SFMPath.fromNative(Objects.requireNonNull(file, "file")
                .toAbsolutePath().normalize());
        return workspace.panelIds().stream()
                .sorted(Comparator.comparingLong(SFMWorkspacePanelId::value))
                .map(panelId -> editor(workspace, panelId))
                .flatMap(Optional::stream)
                .filter(handle -> handle.state().documentSnapshot()
                        .flatMap(SFMTextDocumentSnapshot::path)
                        .filter(expected::equals)
                        .isPresent())
                .findFirst();
    }

    static List<EditorHandle> editors(SFMScreenMultiplexer workspace) {
        return workspace.panelIds().stream()
                .sorted(Comparator.comparingLong(SFMWorkspacePanelId::value))
                .map(panelId -> editor(workspace, panelId))
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * Returns the currently presented workspace status so a live puppet fails
     * with the same typed error a user sees instead of timing out after the
     * transient toast has expired.
     */
    static Optional<String> workspaceToastText(SFMScreenMultiplexer workspace) {
        Objects.requireNonNull(workspace, "workspace");
        try {
            Object toast = WORKSPACE_TOAST.get(workspace);
            if (toast == null) return Optional.empty();
            Field message = toast.getClass().getDeclaredField("message");
            message.setAccessible(true);
            Object value = message.get(toast);
            return value instanceof Component component
                    ? Optional.of(component.getString())
                    : Optional.empty();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not inspect the workspace status toast", failure);
        }
    }

    static Optional<ExplorerHandle> explorer(SFMScreenMultiplexer workspace) {
        return workspace.panelIds().stream()
                .sorted(Comparator.comparingLong(SFMWorkspacePanelId::value))
                .map(panelId -> {
                    Object panel = workspace.panelInstance(panelId);
                    return panel instanceof SFMExplorerPanel explorer
                            ? Optional.of(new ExplorerHandle(panelId, explorer))
                            : Optional.<ExplorerHandle>empty();
                })
                .flatMap(Optional::stream)
                .findFirst();
    }

    static SFMTextDocumentRange symbolRange(String text, String symbol, int occurrence) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(symbol, "symbol");
        if (symbol.isEmpty()) throw new IllegalArgumentException("Symbol must not be empty");
        if (occurrence < -1) throw new IllegalArgumentException("Occurrence must be -1 or non-negative");

        ArrayList<Integer> matches = new ArrayList<>();
        for (int from = 0; from <= text.length() - symbol.length();) {
            int found = text.indexOf(symbol, from);
            if (found < 0) break;
            int end = found + symbol.length();
            if (identifierBoundary(text, found - 1) && identifierBoundary(text, end)) {
                matches.add(found);
            }
            from = found + Math.max(1, symbol.length());
        }
        if (matches.isEmpty()) throw new IllegalArgumentException("Symbol is absent from the source: " + symbol);
        int selected = occurrence == -1 ? matches.get(matches.size() - 1)
                : occurrence < matches.size() ? matches.get(occurrence)
                : -1;
        if (selected < 0) {
            throw new IllegalArgumentException(
                    "Symbol occurrence " + occurrence + " is outside 0.." + (matches.size() - 1));
        }
        return SFMContextTextCoordinates.rangeAtUtf16Offsets(text, selected, selected + symbol.length());
    }

    static String textAtRange(String text, SFMTextDocumentRange range) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(range, "range");
        range.validateAgainst(text);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        int start = range.start().byteOffset();
        int end = range.end().byteOffset();
        return new String(bytes, start, end - start, StandardCharsets.UTF_8);
    }

    static JsonObject range(SFMTextDocumentRange range) {
        JsonObject result = new JsonObject();
        result.add("start", position(range.start().line(), range.start().column(), range.start().byteOffset()));
        result.add("end", position(range.end().line(), range.end().column(), range.end().byteOffset()));
        return result;
    }

    static String canonicalHash(String hash) {
        String value = Objects.requireNonNull(hash, "hash");
        return value.startsWith("sha256:") ? value : "sha256:" + value;
    }

    static SFMTextDocumentSnapshot addressedReadOnlySnapshot(String text, Path path, Path authorizedRoot) {
        Objects.requireNonNull(text, "text");
        SFMTextDocumentSnapshot literal = SFMTextDocumentSnapshot.literal(text);
        return new SFMTextDocumentSnapshot(
                SFMTextDocumentSnapshot.State.READY,
                text,
                SFMTextDocumentSnapshot.MutationCapability.READ_ONLY,
                Optional.of(SFMPath.fromNative(Objects.requireNonNull(path, "path")
                        .toAbsolutePath().normalize())),
                Optional.of(SFMPath.fromNative(Objects.requireNonNull(authorizedRoot, "authorizedRoot")
                        .toAbsolutePath().normalize())),
                literal.sha256(),
                OptionalLong.of(text.getBytes(StandardCharsets.UTF_8).length),
                Optional.empty(),
                Optional.of(SFMResolverTextResult.LineEndingKind.LF),
                Optional.empty(),
                List.of()
        );
    }

    static JsonObject timing(long micros) {
        if (micros < 0) throw new IllegalArgumentException("Timing must not be negative");
        JsonObject result = new JsonObject();
        result.addProperty("micros", micros);
        result.addProperty("exceeds_one_second", micros >= ONE_SECOND_MICROS);
        result.addProperty("multi_second", micros >= MULTI_SECOND_MICROS);
        return result;
    }

    /**
     * Test-only seeding of an unsaved current document while retaining the real
     * immutable disk snapshot used by the definition protocol's stale check.
     */
    static SFMContextDocumentProjection seedReadOnlyCurrentText(
            EditorHandle handle,
            String currentText,
            SFMTextDocumentRange exactRange
    ) {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(currentText, "currentText");
        Objects.requireNonNull(exactRange, "exactRange").validateAgainst(currentText);
        SFMTextEditorPanel editor = handle.resolvedPanel()
                .orElseThrow(() -> new IllegalStateException("The test fixture editor is not resolved"));
        if (!editor.isReadOnly()) throw new IllegalStateException("The test fixture editor must be read-only");
        SFMTextDocumentSnapshot baseline = handle.state().documentSnapshot().orElseThrow();
        try {
            SFMDrawCanvasScreen drawCanvas = drawScreen(editor);
            // Ensure init loaded the real disk baseline before replacing only
            // the current canvas projection. The baseline object remains intact.
            drawCanvas.captureContextProjection(editor.editorId(), baseline, true);
            SFMDrawCanvasModel model = (SFMDrawCanvasModel) DRAW_MODEL.get(drawCanvas);
            model.glyphs().clear();
            model.collapseToFocusedCursor();
            model.setCursor(0.0D, 0.0D);
            model.typeText(currentText, Minecraft.getInstance().font::width,
                    Minecraft.getInstance().font.lineHeight);
            SFMDrawCanvasSyntaxHighlightingHelper.CanvasDocumentProjection projection =
                    SFMDrawCanvasSyntaxHighlightingHelper.projectCanvasDocument(
                            model.glyphs(),
                            Minecraft.getInstance().font.width(" "),
                            Minecraft.getInstance().font.lineHeight
                    );
            if (!projection.text().equals(currentText)) {
                throw new IllegalStateException("Seeded fixture canvas does not project to the exact current text");
            }
            byte[] bytes = currentText.getBytes(StandardCharsets.UTF_8);
            int startByte = exactRange.start().byteOffset();
            int utf16Offset = new String(bytes, 0, startByte, StandardCharsets.UTF_8).length();
            SFMDrawCanvasModel.CanvasGlyph target = projection.glyphsByCharIndex().get(utf16Offset);
            if (target == null) throw new IllegalStateException("Exact fixture range begins on canvas whitespace");
            model.setCursor(target.x(), target.y());
            CONTEXT_GENERATION.setLong(drawCanvas, increment(CONTEXT_GENERATION.getLong(drawCanvas)));
            DOCUMENT_GENERATION.setLong(drawCanvas, increment(DOCUMENT_GENERATION.getLong(drawCanvas)));
            SFMContextDocumentProjection captured = drawCanvas.captureContextProjection(
                    editor.editorId(), baseline, true);
            if (!captured.currentText().equals(currentText) || !captured.dirty() || !captured.readOnly()) {
                throw new IllegalStateException(
                        "Seeded fixture must project as an unsaved, read-only exact current document");
            }
            return captured;
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Could not seed the read-only current-document fixture", failure);
        }
    }

    static SFMContextDocumentProjection captureReadOnlyCurrentText(EditorHandle handle) {
        Objects.requireNonNull(handle, "handle");
        SFMTextEditorPanel editor = handle.resolvedPanel()
                .orElseThrow(() -> new IllegalStateException("The test fixture editor is not resolved"));
        if (!editor.isReadOnly()) throw new IllegalStateException("The test fixture editor must be read-only");
        try {
            return drawScreen(editor).captureContextProjection(
                    editor.editorId(),
                    handle.state().documentSnapshot().orElseThrow(),
                    true
            );
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Could not capture the read-only current-document fixture", failure);
        }
    }

    /**
     * Hashes the exact text projected by the EditorV3 glyph canvas.
     *
     * <p>The ordinary context projection intentionally returns the immutable
     * backing text while an editor is semantically clean. That preserves file
     * identity even when the canvas cannot represent trailing layout. Syntax
     * highlighting, however, styles the glyph projection, so its independent
     * puppet witness must hash that same visual document.</p>
     */
    static String currentCanvasProjectionSha256(EditorHandle handle) {
        Objects.requireNonNull(handle, "handle");
        SFMTextEditorPanel editor = handle.resolvedPanel()
                .orElseThrow(() -> new IllegalStateException("The test fixture editor is not resolved"));
        if (!editor.isReadOnly()) throw new IllegalStateException("The test fixture editor must be read-only");
        try {
            SFMDrawCanvasScreen drawCanvas = drawScreen(editor);
            drawCanvas.captureContextProjection(
                    editor.editorId(),
                    handle.state().documentSnapshot().orElseThrow(),
                    true
            );
            SFMDrawCanvasModel model = (SFMDrawCanvasModel) DRAW_MODEL.get(drawCanvas);
            String projectedText = SFMDrawCanvasSyntaxHighlightingHelper.projectCanvasDocument(
                    List.copyOf(model.glyphs()),
                    Minecraft.getInstance().font.width(" "),
                    Minecraft.getInstance().font.lineHeight
            ).text();
            return SFMSyntaxHighlightRequest.sha256(projectedText);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Could not hash the read-only canvas projection", failure);
        }
    }

    static Optional<EditorHandle> editor(
            SFMScreenMultiplexer workspace,
            SFMWorkspacePanelId panelId
    ) {
        Object panel = workspace.panelInstance(panelId);
        if (!(panel instanceof SFMTextDocumentPanelState state)) return Optional.empty();
        return Optional.of(new EditorHandle(panelId, state, resolvedPanel(panel)));
    }

    private static Optional<SFMTextEditorPanel> resolvedPanel(Object panel) {
        if (panel instanceof SFMTextEditorPanel editor) return Optional.of(editor);
        if (!(panel instanceof SFMDeferredTextEditorPanel)) return Optional.empty();
        try {
            Object delegate = DEFERRED_DELEGATE.get(panel);
            return delegate instanceof SFMTextEditorPanel editor ? Optional.of(editor) : Optional.empty();
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Could not inspect the resolved addressed editor", failure);
        }
    }

    private static Field deferredDelegateField() {
        return field(SFMDeferredTextEditorPanel.class, "delegate");
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static SFMDrawCanvasScreen drawScreen(SFMTextEditorPanel editor) throws IllegalAccessException {
        Screen embedded = (Screen) EDITOR_SCREEN.get(editor);
        if (embedded instanceof SFMDrawCanvasScreen drawCanvas) return drawCanvas;
        throw new IllegalStateException("The test fixture must use Text Editor v3");
    }

    private static long increment(long value) {
        if (value == Long.MAX_VALUE) throw new IllegalStateException("Fixture generation exhausted");
        return value + 1;
    }

    private static boolean identifierBoundary(String text, int offset) {
        if (offset < 0 || offset >= text.length()) return true;
        return !Character.isJavaIdentifierPart(text.codePointAt(offset));
    }

    private static JsonObject position(int line, int column, int byteOffset) {
        JsonObject result = new JsonObject();
        result.addProperty("line", line);
        result.addProperty("column", column);
        result.addProperty("byte_offset", byteOffset);
        return result;
    }
}
