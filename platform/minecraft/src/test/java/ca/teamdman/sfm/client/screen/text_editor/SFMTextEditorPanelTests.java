package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.client.screen.SFMDrawCanvasModel;
import ca.teamdman.sfm.client.screen.SFMDrawCanvasScreen;
import ca.teamdman.sfm.client.context.SFMContextDocumentProjection;
import ca.teamdman.sfm.client.context.SFMContextTextCoordinates;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract;
import ca.teamdman.sfm.client.symbol.SFMDefinitionResult;
import ca.teamdman.sfm.client.symbol.SFMSymbolHoverLookup;
import ca.teamdman.sfm.client.symbol.SFMSymbolHoverStateMachine;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditScreenOpenContext;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorPanelOpenContext;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSelection;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveResult;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import ca.teamdman.sfm.client.symbol.SFMSymbolHoverIdentity;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import sun.misc.Unsafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

class SFMTextEditorPanelTests {
    @Test
    void readOnlyInspectionClosesItsPanelDespiteTerminalNewlineProjection() {
        var closed = new AtomicBoolean();
        var context = new SFMTextEditorPanelOpenContext(
                "sfm:text_editor_v3", "Inspection payload\n", true, "Icon rule explanation");
        var screenContext = SFMTextEditorPanel.screenContext(context, () -> closed.set(true));
        screenContext.onTryClose("Inspection payload", () -> { throw new AssertionError("Must close only its panel"); });
        assertTrue(closed.get());
    }

    @Test
    void focusGainWithStationaryCtrlHoverPreservesLookupAndRecapturesCurrentPointer() {
        List<String> calls = new ArrayList<>();

        SFMTextEditorPanel.reconcileHoverFocusTransition(
                true,
                true,
                new SFMSymbolHoverIdentity.Modifiers(true, false, false, false),
                () -> calls.add("reset"),
                () -> calls.add("refresh")
        );

        assertEquals(List.of("refresh"), calls);
    }

    @Test
    void focusTransitionDoesNotRecaptureWhenInactiveOutsideOrAltModified() {
        List<String> calls = new ArrayList<>();
        Runnable reset = () -> calls.add("reset");
        Runnable refresh = () -> calls.add("refresh");

        SFMTextEditorPanel.reconcileHoverFocusTransition(
                false,
                true,
                new SFMSymbolHoverIdentity.Modifiers(true, false, false, false),
                reset,
                refresh
        );
        SFMTextEditorPanel.reconcileHoverFocusTransition(
                true,
                false,
                new SFMSymbolHoverIdentity.Modifiers(true, false, false, false),
                reset,
                refresh
        );
        SFMTextEditorPanel.reconcileHoverFocusTransition(
                true,
                true,
                new SFMSymbolHoverIdentity.Modifiers(true, true, false, false),
                reset,
                refresh
        );

        assertEquals(List.of("reset"), calls);
    }

    @Test
    void symbolInspectionGeometryAcceptsOnlyTrailingLineEndingProjectionOmission() {
        assertTrue(SFMTextEditorPanel.inspectionProjectionMatches("class A {}", "class A {}"));
        assertTrue(SFMTextEditorPanel.inspectionProjectionMatches("class A {}\n", "class A {}"));
        assertTrue(SFMTextEditorPanel.inspectionProjectionMatches("class A {}\r\n", "class A {}"));
        assertTrue(SFMTextEditorPanel.inspectionProjectionMatches("class A {}\n\n", "class A {}\n"));

        assertFalse(SFMTextEditorPanel.inspectionProjectionMatches("class A { }\n", "class A {}"));
        assertFalse(SFMTextEditorPanel.inspectionProjectionMatches("class A {} ", "class A {}"));
        assertFalse(SFMTextEditorPanel.inspectionProjectionMatches("class A {}", "class B {}"));
    }

    @Test
    void lexicalInspectionRetainsDiagnosticsCoveringTheCapturedByte() {
        var logger = new SFMDefinitionResult.Diagnostic(
                "java.unresolved-type",
                "warning",
                "Could not resolve type reference `Logger` without guessing",
                Optional.of(new SFMDefinitionResult.SourceSpan(
                        "platform/minecraft/src/main/java/ca/teamdman/sfm/SFM.java",
                        "main",
                        "blake3:source",
                        1868,
                        1874,
                        38,
                        25,
                        38,
                        31
                ))
        );
        var unrelated = new SFMDefinitionResult.Diagnostic(
                "java.unresolved-type",
                "warning",
                "Unrelated",
                Optional.of(new SFMDefinitionResult.SourceSpan(
                        "platform/minecraft/src/main/java/ca/teamdman/sfm/SFM.java",
                        "main",
                        "blake3:source",
                        2000,
                        2001,
                        40,
                        1,
                        40,
                        2
                ))
        );

        assertEquals(
                List.of("java.unresolved-type [warning]: Could not resolve type reference `Logger` without guessing"),
                SFMTextEditorPanel.interactionMapDiagnosticsAtByte(List.of(unrelated, logger), 1871)
        );
    }

    @Test
    void ctrlAltClickWithStaleActionableHoverFallsThroughToCanvasMultiCursor() throws Exception {
        SFMTextEditorPanelOpenContext context = new SFMTextEditorPanelOpenContext(
                "sfm:text_editor_v3",
                "name",
                false,
                "Editor"
        );
        RecordingMultiCursorScreen recording = allocateWithoutConstructor(RecordingMultiCursorScreen.class);
        SFMTextEditorPanel panel = SFMTextEditorPanel.legacy(context, openContext -> {
            recording.initialize(openContext);
            return recording;
        });
        SFMSymbolHoverStateMachine hover = new SFMSymbolHoverStateMachine(
                ignored -> new SFMSymbolHoverLookup.Query(
                        CompletableFuture.completedFuture(SFMSymbolHoverLookup.Resolution.ACTIONABLE),
                        () -> {
                        }
                ),
                SFMSymbolHoverStateMachine.DragThreshold.euclidean(4.0D)
        );
        hover.observe(Optional.of(hoverTarget()));
        hover.modifiersChanged(new SFMSymbolHoverIdentity.Modifiers(true, false, false, false));
        assertEquals(SFMSymbolHoverStateMachine.Phase.ACTIONABLE, hover.snapshot().phase());

        setField(panel, "symbolHover", hover);
        setField(
                panel,
                "hoverModifiers",
                new SFMSymbolHoverIdentity.Modifiers(true, true, false, false)
        );

        assertTrue(panel.mouseClicked(64.0D, 9.0D, GLFW.GLFW_MOUSE_BUTTON_LEFT));
        assertEquals(1, recording.clicks);
        assertEquals(2, recording.model.cursors().size());
        assertEquals(64.0D, recording.model.focusedCursor().x());
        assertEquals(9.0D, recording.model.focusedCursor().y());
        assertEquals(SFMSymbolHoverStateMachine.Phase.ACTIONABLE, hover.snapshot().phase(),
                "The stale link state must not receive a definition press when Alt owns the gesture");
    }

    @Test
    void panelContextCarriesEditorIdentityAndReadOnlyState() {
        var context = new SFMTextEditorPanelOpenContext(
                "sfm:text_editor_v3", "class Example {}", true, "Review · Example.java"
        );

        assertEquals("sfm:text_editor_v3", context.editorId());
        assertEquals("class Example {}", context.initialValue());
        assertEquals("Review · Example.java", context.title());
        org.junit.jupiter.api.Assertions.assertTrue(context.readOnly());
    }

    @Test
    void panelContextRejectsMissingIdentityAndContent() {
        assertThrows(IllegalArgumentException.class,
                () -> new SFMTextEditorPanelOpenContext("", "", false, "Editor"));
        assertThrows(IllegalArgumentException.class,
                () -> new SFMTextEditorPanelOpenContext("sfm:text_editor_v3", null, false, "Editor"));
        assertThrows(IllegalArgumentException.class,
                () -> new SFMTextEditorPanelOpenContext("sfm:text_editor_v3", "", false, ""));
    }

    @Test
    void panelContextCarriesTypedSaveSuccessAndRejection() {
        var accepted = new SFMTextEditorPanelOpenContext(
                "sfm:text_editor_v3",
                "before",
                false,
                "Editor",
                content -> content.equals("after")
                        ? SFMTextDocumentSaveResult.success()
                        : SFMTextDocumentSaveResult.rejected(
                                net.minecraft.network.chat.Component.literal("stale")
                        )
        );

        assertTrue(accepted.saveHandler().save("after").saved());
        assertFalse(accepted.saveHandler().save("other").saved());
        assertEquals("stale", accepted.saveHandler().save("other")
                .diagnostic().orElseThrow().getString());
    }

    @Test
    void panelSaveClosesOnlyAfterSuccessAndReturnsRejectionDiagnostic() {
        AtomicBoolean closed = new AtomicBoolean();
        var panelContext = new SFMTextEditorPanelOpenContext(
                "sfm:v1",
                "before",
                false,
                "Explorer Location",
                content -> content.equals("accepted")
                        ? SFMTextDocumentSaveResult.success()
                        : SFMTextDocumentSaveResult.rejected(
                                net.minecraft.network.chat.Component.literal("stale revision")
                        )
        );
        var screenContext = SFMTextEditorPanel.screenContext(panelContext, () -> closed.set(true));
        assertEquals(panelContext.document(), screenContext.documentSnapshot().orElseThrow());

        SFMTextDocumentSaveResult rejected = screenContext.trySaveAndClose("rejected");
        assertFalse(rejected.saved());
        assertEquals("stale revision", rejected.diagnostic().orElseThrow().getString());
        assertFalse(closed.get());

        assertTrue(screenContext.trySaveAndClose("accepted").saved());
        assertTrue(closed.get());
    }

    @Test
    void asynchronousPanelContextAdvancesOnlyTheSubmittedBaselineOnClientAcknowledgement() {
        var durable = new java.util.concurrent.CompletableFuture<SFMTextDocumentSaveResult>();
        var closed = new AtomicBoolean();
        var handler = new ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveHandler() {
            @Override public boolean asynchronous() { return true; }
            @Override public SFMTextDocumentSaveResult save(String text) { throw new AssertionError("sync save invoked"); }
            @Override public java.util.concurrent.CompletableFuture<SFMTextDocumentSaveResult> saveAsync(String text) {
                assertEquals("submitted", text);
                return durable;
            }
        };
        var panelContext = new SFMTextEditorPanelOpenContext("sfm:v1", "baseline", false, "Comment", handler);
        var saved = new java.util.ArrayList<String>();
        var screenContext = SFMTextEditorPanel.screenContext(panelContext, () -> closed.set(true), saved::add);
        assertTrue(screenContext.asynchronousSave());
        assertSame(durable, screenContext.saveDocumentAsync("submitted"));
        assertEquals("baseline", screenContext.initialValue());
        assertFalse(closed.get());
        durable.complete(SFMTextDocumentSaveResult.success());
        assertEquals("baseline", screenContext.initialValue(), "worker completion alone is not a UI acknowledgement");
        screenContext.documentSaved("submitted");
        assertEquals("submitted", screenContext.initialValue());
        assertEquals(java.util.List.of("submitted"), saved);
        assertFalse(closed.get());
        screenContext.finishAsyncSaveClose();
        assertTrue(closed.get());
    }

    @Test
    void reusedEditorPublishesTheLatestNavigationTargetRange() throws Exception {
        String text = "class A { void first() {} void second() {} }";
        SFMTextDocumentRange first = rangeOf(text, "first");
        SFMTextDocumentRange second = rangeOf(text, "second");
        SFMTextDocumentSnapshot baseline = withTargetRange(SFMTextDocumentSnapshot.literal(text), first);
        SFMTextEditorPanelOpenContext context = new SFMTextEditorPanelOpenContext(
                "sfm:text_editor_v3",
                baseline,
                true,
                "A.java",
                ignored -> SFMTextDocumentSaveResult.success()
        );
        RecordingNavigationScreen screen = allocateWithoutConstructor(RecordingNavigationScreen.class);
        setField(screen, "text", text);
        setField(screen, "navigatedRange", Optional.empty());
        var constructor = SFMTextEditorPanel.class.getDeclaredConstructor(
                SFMTextEditorPanelOpenContext.class,
                Screen.class,
                boolean.class
        );
        constructor.setAccessible(true);
        SFMTextEditorPanel panel = constructor.newInstance(context, screen, false);

        assertEquals(Optional.of(first), panel.documentSnapshot().orElseThrow().targetRange());
        assertTrue(panel.navigateToRange(second));
        assertEquals(Optional.of(second), screen.navigatedRange);
        assertEquals(withTargetRange(baseline, second), panel.documentSnapshot().orElseThrow(),
                "A reused editor must publish the exact range it now presents without losing provenance");
    }

    @Test
    void historyCheckoutRoundTripsDirectionalUnicodeAndMultipleSelections() throws Exception {
        String text = "a😀bc";
        SFMDocumentHistoryContract.DocumentState expected = new SFMDocumentHistoryContract.DocumentState(
                text,
                List.of(
                        logicalSelection(text, "forward", 1, 3),
                        logicalSelection(text, "backward", 4, 2)
                ),
                Optional.of("forward")
        );
        SFMTextEditorPanelOpenContext context = new SFMTextEditorPanelOpenContext(
                "sfm:text_editor_v3", text, false, "Editor");
        RecordingHistorySelectionScreen screen = allocateWithoutConstructor(RecordingHistorySelectionScreen.class);
        setField(screen, "text", text);
        setField(screen, "selections", List.of());
        var constructor = SFMTextEditorPanel.class.getDeclaredConstructor(
                SFMTextEditorPanelOpenContext.class,
                Screen.class,
                boolean.class
        );
        constructor.setAccessible(true);
        SFMTextEditorPanel panel = constructor.newInstance(context, screen, true);

        panel.checkoutDocumentHistoryState(expected);

        assertEquals(2, screen.selections.size());
        assertEquals("forward", screen.selections.get(0).id(),
                "the primary selection must drive the spatial primary cursor");
        assertTrue(screen.selections.get(0).primary());
        assertTrue(screen.selections.stream().anyMatch(selection ->
                selection.id().equals("backward")
                        && selection.anchor().byteOffset() > selection.active().byteOffset()));
        assertEquals(expected, panel.captureDocumentHistoryState(screen));
    }

    private static SFMSymbolHoverStateMachine.Target hoverTarget() {
        return new SFMSymbolHoverStateMachine.Target(
                new SFMSymbolHoverIdentity.EditorOrigin(
                        "screen-1",
                        "workspace-1",
                        "stack-1",
                        "panel-1",
                        "editor-1",
                        1L
                ),
                new SFMSymbolHoverIdentity.DocumentVersion(
                        "file:///workspace/A.java",
                        "content-hash:name",
                        1L
                ),
                SFMSymbolHoverIdentity.TextGlyphRange.fromUtf16("name", 0, 4, 0, 4),
                new SFMSymbolHoverIdentity.SemanticContext(
                        "lexical-identifier",
                        0,
                        Optional.empty(),
                        Optional.empty(),
                        0
                )
        );
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static <T> T allocateWithoutConstructor(Class<T> type) throws Exception {
        var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return type.cast(((Unsafe) unsafeField.get(null)).allocateInstance(type));
    }

    private static SFMTextDocumentRange rangeOf(String text, String token) {
        int start = text.indexOf(token);
        if (start < 0) throw new IllegalArgumentException("Missing token " + token);
        return new SFMTextDocumentRange(
                SFMTextDocumentRange.positionAtByteOffset(text, start),
                SFMTextDocumentRange.positionAtByteOffset(text, start + token.length())
        );
    }

    private static SFMDocumentHistoryContract.LogicalSelection logicalSelection(
            String text,
            String id,
            int anchorCodePoint,
            int activeCodePoint
    ) {
        return new SFMDocumentHistoryContract.LogicalSelection(
                id,
                SFMDocumentHistoryContract.LogicalPoint.at(text, anchorCodePoint),
                SFMDocumentHistoryContract.LogicalPoint.at(text, activeCodePoint)
        );
    }

    private static SFMTextDocumentSnapshot withTargetRange(
            SFMTextDocumentSnapshot document,
            SFMTextDocumentRange range
    ) {
        return new SFMTextDocumentSnapshot(
                document.state(),
                document.text(),
                document.mutationCapability(),
                document.path(),
                document.authorizedRoot(),
                document.sha256(),
                document.byteLength(),
                document.lastModified(),
                document.lineEndingKind(),
                Optional.of(range),
                document.diagnostics()
        );
    }

    private static final class RecordingMultiCursorScreen extends Screen implements ISFMTextEditScreen {
        private ISFMTextEditScreenOpenContext openContext;
        private SFMDrawCanvasModel model;
        private int clicks;

        private RecordingMultiCursorScreen() {
            super(Component.literal("Recording multi-cursor screen"));
        }

        private void initialize(ISFMTextEditScreenOpenContext openContext) {
            this.openContext = openContext;
            this.model = new SFMDrawCanvasModel();
        }

        @Override
        public ISFMTextEditScreenOpenContext openContext() {
            return openContext;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
            clicks++;
            model.addCursor(mouseX, mouseY);
            return true;
        }
    }

    private static final class RecordingNavigationScreen extends SFMDrawCanvasScreen {
        private String text;
        private Optional<SFMTextDocumentRange> navigatedRange;

        private RecordingNavigationScreen() {
            super((Screen) null);
        }

        @Override
        public SFMContextDocumentProjection captureContextProjection(
                String editorId,
                SFMTextDocumentSnapshot baseline,
                boolean readOnly
        ) {
            return SFMContextDocumentProjection.capture(
                    editorId,
                    baseline,
                    text,
                    false,
                    readOnly,
                    List.of(),
                    List.of()
            );
        }

        @Override
        public void openAtTextRange(SFMTextDocumentRange range) {
            range.validateAgainst(text);
            navigatedRange = Optional.of(range);
        }
    }

    private static final class RecordingHistorySelectionScreen extends SFMDrawCanvasScreen {
        private String text;
        private List<SFMTextDocumentSelection> selections = List.of();

        private RecordingHistorySelectionScreen(String text) {
            super((Screen) null);
            this.text = text;
        }

        @Override
        public void checkoutDocumentSelections(String text, List<SFMTextDocumentSelection> selections) {
            this.text = text;
            this.selections = List.copyOf(selections);
        }

        @Override
        public Optional<List<SFMTextDocumentSelection>> exactDocumentSelections() {
            return Optional.of(selections);
        }

        @Override
        public SFMContextDocumentProjection captureContextProjection(
                String editorId,
                SFMTextDocumentSnapshot baseline,
                boolean readOnly
        ) {
            return SFMContextDocumentProjection.capture(
                    editorId,
                    baseline,
                    text,
                    !baseline.displayText().equals(text),
                    readOnly,
                    List.of(),
                    selections.stream()
                            .map(selection -> new ca.teamdman.sfm.client.context.SFMContextSelectionProjection(
                                    selection.id(), List.of(selection.orderedRange()), selection.primary()))
                            .toList()
            );
        }
    }
}
