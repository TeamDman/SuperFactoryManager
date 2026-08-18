package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.context.SFMContextContribution;
import ca.teamdman.sfm.client.context.SFMContextDocumentProjection;
import ca.teamdman.sfm.client.context.SFMContextOriginId;
import ca.teamdman.sfm.client.screen.SFMDrawCanvasDocumentIndex;
import ca.teamdman.sfm.client.screen.SFMDrawCanvasModel;
import ca.teamdman.sfm.client.screen.SFMDrawCanvasPerformanceTracker;
import ca.teamdman.sfm.client.screen.SFMDrawCanvasScreen;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel;
import ca.teamdman.sfm.client.screen.text_editor.SFMDeferredTextEditorPanel;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextDocumentPanelState;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditorPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.semantic.SFMSpatialCoverageService;
import ca.teamdman.sfm.client.semantic.SFMSpatialSemanticContract;
import ca.teamdman.sfm.client.symbol.SFMSymbolHoverIdentity;
import ca.teamdman.sfm.client.symbol.SFMSymbolHoverStateMachine;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** C-11-only live probes; no production behavior depends on these reflective witnesses. */
final class C11SourceNavigationPuppetProbe {
    record Pointer(
            double canvasX,
            double canvasY,
            double localX,
            double localY,
            double globalX,
            double globalY,
            SFMDrawCanvasScreen.SymbolHit hit
    ) {
        Pointer {
            Objects.requireNonNull(hit, "hit");
        }
    }

    record Hover(
            SFMSymbolHoverStateMachine.Snapshot snapshot,
            Optional<SFMSymbolHoverIdentity.TextGlyphRange> renderedUnderline,
            boolean handCursorSelected,
            SFMSymbolHoverStateMachine.CancellationCause lastCancellationCause
    ) {
        Hover {
            Objects.requireNonNull(snapshot, "snapshot");
            renderedUnderline = Objects.requireNonNull(renderedUnderline, "renderedUnderline");
            Objects.requireNonNull(lastCancellationCause, "lastCancellationCause");
        }
    }

    record SpatialWitness(
            SFMSpatialSemanticContract.SnapshotIdentity snapshot,
            SFMSpatialCoverageService.Observation observation
    ) {
        SpatialWitness {
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(observation, "observation");
            SFMSpatialSemanticContract.Probe probe = observation.probe();
            if (probe.workspaceGeneration() != snapshot.workspaceGeneration()
                    || probe.documentGeneration() != snapshot.documentGeneration()
                    || probe.semanticGeneration() != snapshot.semanticGeneration()
                    || probe.layoutGeneration() != snapshot.layoutGeneration()) {
                throw new IllegalArgumentException("Spatial witness generations do not match its snapshot");
            }
        }
    }

    private static final Field DEFERRED_DELEGATE = field(SFMDeferredTextEditorPanel.class, "delegate");
    private static final Field EDITOR_SCREEN = field(SFMTextEditorPanel.class, "screen");
    private static final Field EDITOR_SYMBOL_HOVER = field(SFMTextEditorPanel.class, "symbolHover");
    private static final Field EDITOR_LINK_CURSOR = field(SFMTextEditorPanel.class, "linkCursor");
    private static final Field DRAW_MODEL = field(SFMDrawCanvasScreen.class, "model");
    private static final Field DRAW_CAMERA_X = field(SFMDrawCanvasScreen.class, "cameraX");
    private static final Field DRAW_CAMERA_Y = field(SFMDrawCanvasScreen.class, "cameraY");
    private static final Field DRAW_ZOOM = field(SFMDrawCanvasScreen.class, "zoom");
    private static final Field DRAW_OPEN_TARGET = field(SFMDrawCanvasScreen.class, "openTargetRange");
    private static final Field DRAW_HOVER_UNDERLINE = field(SFMDrawCanvasScreen.class, "symbolHoverUnderline");

    private C11SourceNavigationPuppetProbe() {
    }

    static Optional<SFMSourcePuppetProbe.EditorHandle> editor(SFMScreenMultiplexer workspace, Path path) {
        return SFMSourcePuppetProbe.editor(workspace, path);
    }

    static Optional<SFMSourcePuppetProbe.EditorHandle> focusedEditor(SFMScreenMultiplexer workspace) {
        return editor(workspace, workspace.focusedPanelId());
    }

    static List<SFMSourcePuppetProbe.EditorHandle> editors(SFMScreenMultiplexer workspace) {
        return SFMSourcePuppetProbe.editors(workspace);
    }

    static Optional<SFMSourcePuppetProbe.EditorHandle> editor(
            SFMScreenMultiplexer workspace,
            SFMWorkspacePanelId panelId
    ) {
        Object panel = workspace.panelInstance(panelId);
        if (!(panel instanceof SFMTextDocumentPanelState state)) return Optional.empty();
        Optional<SFMTextEditorPanel> resolved = resolvedPanel(panel);
        return Optional.of(new SFMSourcePuppetProbe.EditorHandle(panelId, state, resolved));
    }

    static Pointer pointer(
            SFMScreenMultiplexer workspace,
            SFMSourcePuppetProbe.EditorHandle editor,
            SFMTextDocumentRange range
    ) {
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(editor, "editor");
        Objects.requireNonNull(range, "range");
        SFMTextEditorPanel panel = editor.resolvedPanel().orElseThrow(() ->
                new IllegalStateException("The source editor has not resolved to Text Editor v3"));
        SFMTextDocumentSnapshot document = editor.state().documentSnapshot().orElseThrow();
        range.validateAgainst(document.text());
        if (!panel.navigateToRange(range)) {
            throw new IllegalStateException("The source editor could not navigate to the pointer range");
        }
        try {
            SFMDrawCanvasScreen draw = draw(panel);
            SFMDrawCanvasModel model = (SFMDrawCanvasModel) DRAW_MODEL.get(draw);
            SFMDrawCanvasDocumentIndex index = model.documentIndex(
                    Minecraft.getInstance().font.width(" "),
                    Minecraft.getInstance().font.lineHeight
            );
            int utf16 = utf16Offset(document.text(), range.start().byteOffset());
            SFMDrawCanvasModel.CanvasGlyph glyph = index.projection().glyphsByCharIndex().get(utf16);
            if (glyph == null) throw new IllegalStateException("The pointer range does not begin on a glyph");
            double zoom = DRAW_ZOOM.getDouble(draw);
            double canvasX = glyph.x() + Math.max(0.5D, glyph.width() / 2.0D);
            double canvasY = glyph.y() + Math.max(0.5D, Minecraft.getInstance().font.lineHeight / 2.0D);
            double localX = (glyph.x() - DRAW_CAMERA_X.getDouble(draw)) * zoom
                    + draw.width / 2.0D + Math.max(0.5D, glyph.width() * zoom / 2.0D);
            double localY = (glyph.y() - DRAW_CAMERA_Y.getDouble(draw)) * zoom
                    + draw.height / 2.0D + Math.max(0.5D, Minecraft.getInstance().font.lineHeight * zoom / 2.0D);
            SFMScreenPanelBounds local = new SFMScreenPanelBounds(
                    (int) Math.floor(localX),
                    (int) Math.floor(localY),
                    1,
                    1
            );
            SFMScreenPanelBounds global = workspace.measure(editor.panelId(), local)
                    .orElseThrow(() -> new IllegalStateException("The source pointer could not be mapped globally"))
                    .globalGuiLogicalBounds();
            double globalX = global.x() + global.width() / 2.0D;
            double globalY = global.y() + global.height() / 2.0D;
            SFMDrawCanvasScreen.SymbolHit hit = draw.symbolHitAtScreen(localX, localY)
                    .orElseThrow(() -> new IllegalStateException("The mapped source pointer does not hit a symbol"));
            return new Pointer(canvasX, canvasY, localX, localY, globalX, globalY, hit);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Could not inspect the source editor pointer geometry", failure);
        }
    }

    static Hover hover(SFMSourcePuppetProbe.EditorHandle editor) {
        SFMTextEditorPanel panel = editor.resolvedPanel().orElseThrow();
        try {
            SFMSymbolHoverStateMachine state = (SFMSymbolHoverStateMachine) EDITOR_SYMBOL_HOVER.get(panel);
            if (state == null) throw new IllegalStateException("The EditorV3 hover state is unavailable");
            Object cursor = EDITOR_LINK_CURSOR.get(panel);
            boolean selected = cursor != null && booleanField(cursor, "selected").getBoolean(cursor);
            @SuppressWarnings("unchecked")
            Optional<SFMSymbolHoverIdentity.TextGlyphRange> underline =
                    (Optional<SFMSymbolHoverIdentity.TextGlyphRange>) DRAW_HOVER_UNDERLINE.get(draw(panel));
            return new Hover(state.snapshot(), underline, selected, state.lastCancellationCause());
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Could not inspect the EditorV3 hover state", failure);
        }
    }

    static Optional<SFMTextEditorPanel.SpatialCoverageCapture> spatialCoverage(
            SFMSourcePuppetProbe.EditorHandle editor
    ) {
        return editor.resolvedPanel().flatMap(SFMTextEditorPanel::captureSpatialCoverage);
    }

    static Optional<SpatialWitness> spatialWitness(
            SFMSourcePuppetProbe.EditorHandle editor,
            Pointer pointer
    ) {
        Objects.requireNonNull(pointer, "pointer");
        return spatialCoverage(editor).map(capture -> new SpatialWitness(
                capture.snapshot(),
                capture.document().oracle().probe(pointer.canvasX(), pointer.canvasY())
        ));
    }

    static Optional<SFMSpatialSemanticContract.FramingObservation> framing(
            SFMSourcePuppetProbe.EditorHandle editor
    ) {
        try {
            return draw(editor.resolvedPanel().orElseThrow()).navigationFramingObservation();
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Could not inspect EditorV3 navigation framing", failure);
        }
    }

    static Optional<SFMTextDocumentRange> openTargetRange(SFMSourcePuppetProbe.EditorHandle editor) {
        SFMTextEditorPanel panel = editor.resolvedPanel().orElseThrow();
        try {
            @SuppressWarnings("unchecked")
            Optional<SFMTextDocumentRange> target =
                    (Optional<SFMTextDocumentRange>) DRAW_OPEN_TARGET.get(draw(panel));
            return target;
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Could not inspect the EditorV3 open target", failure);
        }
    }

    static SFMDrawCanvasPerformanceTracker.Snapshot performance(
            SFMSourcePuppetProbe.EditorHandle editor
    ) {
        try {
            return draw(editor.resolvedPanel().orElseThrow()).performanceEvidence();
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Could not inspect EditorV3 performance evidence", failure);
        }
    }

    static void beginWarmPerformanceMeasurement(SFMSourcePuppetProbe.EditorHandle editor) {
        try {
            draw(editor.resolvedPanel().orElseThrow()).beginWarmPerformanceMeasurement();
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Could not begin the EditorV3 warm performance measurement", failure);
        }
    }

    static Optional<SFMContextContribution> focusedDocument(SFMScreenMultiplexer workspace) {
        var snapshot = workspace.contextSnapshot();
        Optional<SFMContextOriginId> focused = snapshot.focusedOriginId();
        if (focused.isEmpty()) return Optional.empty();
        return snapshot.contributions().stream()
                .filter(contribution -> contribution.originId().equals(focused.orElseThrow()))
                .filter(contribution -> contribution.projection() instanceof SFMContextDocumentProjection)
                .findFirst();
    }

    static Optional<SFMSourcePuppetProbe.ExplorerHandle> referenceExplorer(
            SFMScreenMultiplexer workspace,
            java.util.Set<SFMWorkspacePanelId> excluded
    ) {
        return workspace.panelIds().stream()
                .filter(id -> !excluded.contains(id))
                .sorted(Comparator.comparingLong(SFMWorkspacePanelId::value))
                .map(id -> {
                    Object panel = workspace.panelInstance(id);
                    if (!(panel instanceof SFMExplorerPanel explorer)) {
                        return Optional.<SFMSourcePuppetProbe.ExplorerHandle>empty();
                    }
                    boolean references = explorer.sessionSnapshot().roots().stream()
                            .anyMatch(root -> root.scheme().equals("symbol-references"));
                    return references
                            ? Optional.of(new SFMSourcePuppetProbe.ExplorerHandle(id, explorer))
                            : Optional.<SFMSourcePuppetProbe.ExplorerHandle>empty();
                })
                .flatMap(Optional::stream)
                .findFirst();
    }

    static JsonObject topology(SFMScreenMultiplexer workspace) {
        JsonObject result = new JsonObject();
        result.addProperty("focused_panel", workspace.focusedPanelId().toString());
        result.addProperty("total_entries", workspace.panelIds().size());
        result.addProperty("visible_entries", workspace.visiblePanelEntries().size());
        JsonArray panels = new JsonArray();
        for (SFMWorkspacePanelId id : workspace.panelIds().stream()
                .sorted(Comparator.comparingLong(SFMWorkspacePanelId::value)).toList()) {
            JsonObject panel = new JsonObject();
            panel.addProperty("panel_id", id.toString());
            panel.addProperty("stack_id", workspace.panelStackId(id).map(Object::toString).orElse("none"));
            Object instance = workspace.panelInstance(id);
            panel.addProperty("type", instance == null ? "missing" : instance.getClass().getName());
            panel.addProperty("visible", workspace.visiblePanelEntries().stream()
                    .anyMatch(entry -> entry.id().equals(id)));
            panels.add(panel);
        }
        result.add("panels", panels);
        return result;
    }

    static JsonObject range(SFMTextDocumentRange range) {
        return SFMSourcePuppetProbe.range(range);
    }

    static String textAtRange(String text, SFMTextDocumentRange range) {
        return SFMSourcePuppetProbe.textAtRange(text, range);
    }

    private static Optional<SFMTextEditorPanel> resolvedPanel(Object panel) {
        if (panel instanceof SFMTextEditorPanel editor) return Optional.of(editor);
        if (!(panel instanceof SFMDeferredTextEditorPanel)) return Optional.empty();
        try {
            Object delegate = DEFERRED_DELEGATE.get(panel);
            return delegate instanceof SFMTextEditorPanel editor ? Optional.of(editor) : Optional.empty();
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Could not inspect a deferred EditorV3 panel", failure);
        }
    }

    private static SFMDrawCanvasScreen draw(SFMTextEditorPanel editor) throws IllegalAccessException {
        Screen embedded = (Screen) EDITOR_SCREEN.get(editor);
        if (embedded instanceof SFMDrawCanvasScreen draw) return draw;
        throw new IllegalStateException("The C-11 source journey requires Text Editor v3");
    }

    private static int utf16Offset(String text, int byteOffset) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (byteOffset < 0 || byteOffset > bytes.length) {
            throw new IllegalArgumentException("Byte offset lies outside the document");
        }
        return new String(bytes, 0, byteOffset, StandardCharsets.UTF_8).length();
    }

    private static Field booleanField(Object owner, String name) {
        return field(owner.getClass(), name);
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
}
