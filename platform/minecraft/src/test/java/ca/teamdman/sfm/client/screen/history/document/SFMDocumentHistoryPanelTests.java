package ca.teamdman.sfm.client.screen.history.document;

import ca.teamdman.sfm.client.history.SFMDocumentHistoryHostController;
import ca.teamdman.sfm.client.history.canvas.SFMHistoryCanvasLayout;
import ca.teamdman.sfm.client.history.canvas.SFMHistoryCanvasSpatialIndex;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistorySession;
import ca.teamdman.sfm.client.history.document.runtime.SFMDocumentHistoryRuntime;
import ca.teamdman.sfm.client.history.document.runtime.SFMDocumentHistorySelector;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMDocumentHistoryPanelTests {
    @Test
    void automationGeometryIsAbsentBeforeLayoutAndTracksResizes() {
        Fixture fixture = new Fixture();
        try {
            assertTrue(fixture.panel.readyCanvasBoundsForAutomation().isEmpty());
            fixture.open(new SFMScreenPanelBounds(4, 8, 640, 360));
            long firstGeneration = fixture.panel.geometryGeneration();
            assertTrue(firstGeneration > 0);
            assertEquals(new SFMScreenPanelBounds(4, 34, 640, 268),
                    fixture.panel.readyCanvasBoundsForAutomation().orElseThrow());

            fixture.panel.resized(null, new SFMScreenPanelBounds(10, 12, 213, 240));
            assertTrue(fixture.panel.geometryGeneration() > firstGeneration);
            assertEquals(new SFMScreenPanelBounds(10, 38, 213, 148),
                    fixture.panel.readyCanvasBoundsForAutomation().orElseThrow());
        } finally {
            fixture.close();
        }
    }

    @Test
    void queuedProjectionSurvivesTransposeAndPublishesRequestedOrientation() {
        Fixture fixture = new Fixture();
        try {
            fixture.open();
            assertEquals(SFMDocumentHistoryPanel.LoadStatus.QUEUED, fixture.panel.loadStatus());

            fixture.panel.transpose();
            fixture.executor.drain();
            fixture.panel.tick();

            assertEquals(SFMDocumentHistoryPanel.LoadStatus.READY, fixture.panel.loadStatus());
            assertEquals(SFMHistoryCanvasLayout.Orientation.LEFT_RIGHT, fixture.panel.orientation());
            assertEquals(SFMHistoryCanvasLayout.Orientation.LEFT_RIGHT,
                    fixture.panel.layoutSnapshot().orElseThrow().orientation());
            assertFalse(fixture.panel.accessibleTranscript().isEmpty());
        } finally {
            fixture.close();
        }
    }

    @Test
    void pushedRevisionRetainsStableSelectionAndExposesTranscriptFallbackAndDetails() {
        Fixture fixture = new Fixture();
        try {
            fixture.openReady();
            String root = fixture.session.currentRevisionId();
            assertTrue(fixture.panel.selectSubject(new SFMHistoryCanvasSpatialIndex.Subject(
                    SFMHistoryCanvasLayout.SubjectKind.NODE,
                    root
            )));
            long beforePublication = fixture.panel.publicationGeneration();

            fixture.append("hello", 7);
            fixture.panel.tick();
            fixture.executor.drain();
            fixture.panel.tick();

            assertTrue(fixture.panel.publicationGeneration() > beforePublication);
            assertEquals(root, fixture.panel.selectedSubject().orElseThrow().stableId());
            assertTrue(fixture.panel.accessibleTranscript().stream().anyMatch(line ->
                    line.toLowerCase(java.util.Locale.ROOT).contains("hello")));

            fixture.panel.togglePresentationMode();
            assertEquals(SFMDocumentHistoryPanel.PresentationMode.TRANSCRIPT,
                    fixture.panel.presentationMode());
            assertTrue(fixture.panel.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
            assertTrue(fixture.panel.detailsExpanded());

            SFMDocumentHistoryPanelArtifact artifact = fixture.panel.artifact();
            assertEquals(SFMDocumentHistoryPanelArtifact.SCHEMA, artifact.schema());
            assertEquals(fixture.session.identity().sessionId(), artifact.resolvedSessionId().orElseThrow());
            assertEquals(fixture.session.projection(), artifact.documentProjection().orElseThrow());
            assertEquals(fixture.panel.accessibleTranscript(), artifact.accessibleTranscript());
        } finally {
            fixture.close();
        }
    }

    @Test
    void untouchedNarrowPanelFramesTheCurrentHeadAtReadableWidth() {
        Fixture fixture = new Fixture();
        try {
            fixture.openReady(new SFMScreenPanelBounds(0, 0, 213, 240));
            for (int index = 0; index < 9; index++) {
                fixture.append(Character.toString('a' + index), 100L * (index + 1));
            }
            fixture.panel.tick();
            fixture.executor.drain();
            fixture.panel.tick();

            assertTrue(fixture.panel.viewport().zoom() > SFMDocumentHistoryViewport.MIN_ZOOM);
            SFMHistoryCanvasLayout.Node head = fixture.panel.layoutSnapshot().orElseThrow().nodes().stream()
                    .filter(node -> node.stableId().equals(fixture.session.currentRevisionId()))
                    .findFirst()
                    .orElseThrow();
            assertTrue(fixture.panel.viewport().visibleCanvasBounds(
                    fixture.panel.canvasBounds().width(),
                    fixture.panel.canvasBounds().height()
            ).intersects(head.bounds()));
        } finally {
            fixture.close();
        }
    }

    @Test
    void transposedFrameFitsLanesAndCentersTheCurrentSubjectWithoutShrinkingTheTimeline() {
        SFMHistoryCanvasLayout.Rect content = new SFMHistoryCanvasLayout.Rect(20, 40, 1_200, 300);
        SFMHistoryCanvasLayout.Rect focus = new SFMHistoryCanvasLayout.Rect(1_000, 120, 80, 40);

        SFMDocumentHistoryViewport viewport = SFMDocumentHistoryViewport.frameHeightAround(
                content, focus, 480, 360, 10);

        assertEquals(1.0D, viewport.zoom());
        assertEquals(240.0D, viewport.canvasToScreenX(focus.x() + focus.width() / 2.0D));
        assertEquals(30.0D, viewport.canvasToScreenY(content.y()));

        SFMDocumentHistoryViewport longHistory = SFMDocumentHistoryViewport.frameHeightAround(
                new SFMHistoryCanvasLayout.Rect(0, 0, 4_000, 2_000),
                focus,
                480,
                360,
                10
        );
        assertEquals(SFMDocumentHistoryViewport.MIN_READABLE_FRAME_ZOOM, longHistory.zoom());
        assertEquals(240.0D, longHistory.canvasToScreenX(focus.x() + focus.width() / 2.0D));
    }

    @Test
    void resetKeyFramesTheCurrentSubjectInLeftRightOrientation() {
        Fixture fixture = new Fixture();
        try {
            fixture.openReady(new SFMScreenPanelBounds(0, 0, 480, 320));
            for (int index = 0; index < 12; index++) {
                fixture.append(Character.toString('a' + index), 100L * (index + 1));
            }
            fixture.panel.tick();
            fixture.executor.drain();
            fixture.panel.tick();
            fixture.panel.transpose();
            fixture.panel.mouseScrolled(240.0D, 150.0D, -3.0D);

            assertTrue(fixture.panel.keyPressed(GLFW.GLFW_KEY_R, 0, 0));
            SFMHistoryCanvasLayout.Node head = fixture.panel.layoutSnapshot().orElseThrow().nodes().stream()
                    .filter(node -> node.stableId().equals(fixture.session.currentRevisionId()))
                    .findFirst()
                    .orElseThrow();
            assertTrue(fixture.panel.viewport().zoom() > SFMDocumentHistoryViewport.MIN_ZOOM);
            assertTrue(fixture.panel.viewport().visibleCanvasBounds(
                    fixture.panel.canvasBounds().width(),
                    fixture.panel.canvasBounds().height()
            ).intersects(head.bounds()));
        } finally {
            fixture.close();
        }
    }

    @Test
    void mouseHoverAndClickSelectTheRasterizedCurrentHead() {
        Fixture fixture = new Fixture();
        try {
            fixture.openReady(new SFMScreenPanelBounds(7, 11, 480, 300));
            SFMHistoryCanvasLayout.Node head = fixture.panel.layoutSnapshot().orElseThrow().nodes().stream()
                    .filter(node -> node.stableId().equals(fixture.session.currentRevisionId()))
                    .findFirst()
                    .orElseThrow();
            double canvasX = head.bounds().x() + head.bounds().width() / 2.0D;
            double canvasY = head.bounds().y() + head.bounds().height() / 2.0D;
            SFMScreenPanelBounds canvasBounds = fixture.panel.readyCanvasBoundsForAutomation().orElseThrow();
            double screenX = canvasBounds.x() + fixture.panel.viewport().canvasToScreenX(canvasX);
            double screenY = canvasBounds.y() + fixture.panel.viewport().canvasToScreenY(canvasY);

            fixture.panel.mouseMoved(screenX, screenY);
            assertEquals(head.stableId(), fixture.panel.hoveredSubject().orElseThrow().stableId());
            assertTrue(fixture.panel.mouseClicked(screenX, screenY, GLFW.GLFW_MOUSE_BUTTON_LEFT));
            assertEquals(head.stableId(), fixture.panel.selectedSubject().orElseThrow().stableId());
        } finally {
            fixture.close();
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final SFMDocumentHistoryRuntime runtime = new SFMDocumentHistoryRuntime();
        private final SFMDocumentHistorySession session = SFMDocumentHistorySession.create(
                new SFMDocumentHistoryContract.SessionIdentity(
                        "sfm:test/panel-history",
                        "sfm:test/document",
                        Optional.empty()
                ),
                SFMDocumentHistoryContract.DocumentState.withCaret("", 0)
        );
        private final SFMDocumentHistoryHostController controller = new SFMDocumentHistoryHostController(
                session,
                "test",
                "panel",
                ignored -> { }
        );
        private final ManualExecutor executor = new ManualExecutor();
        private final SFMDocumentHistoryPanel panel = new SFMDocumentHistoryPanel(
                runtime,
                SFMDocumentHistorySelector.focused(),
                executor
        );
        private final SFMDocumentHistoryRuntime.Registration registration = runtime.registerFocused(controller);
        private boolean opened;

        private void open() {
            open(new SFMScreenPanelBounds(0, 0, 640, 360));
        }

        private void open(SFMScreenPanelBounds bounds) {
            panel.opened(
                    null,
                    bounds,
                    SFMWorkspacePanelContext.unhosted(new SFMWorkspacePanelId(1))
            );
            opened = true;
            panel.tick();
        }

        private void openReady() {
            open();
            ready();
        }

        private void openReady(SFMScreenPanelBounds bounds) {
            open(bounds);
            ready();
        }

        private void ready() {
            executor.drain();
            panel.tick();
            assertEquals(SFMDocumentHistoryPanel.LoadStatus.READY, panel.loadStatus());
        }

        private void append(String text, long tick) {
            String before = session.currentState().text();
            String after = before + text;
            controller.recordRawInput(
                    tick,
                    SFMDocumentHistoryContract.RawEventKind.CHARACTER,
                    "screen",
                    text,
                    Optional.of(text),
                    0,
                    false,
                    true
            );
            controller.observeMutation(
                    SFMDocumentHistoryContract.MutationKind.TYPE,
                    SFMDocumentHistoryContract.EditDirection.FORWARD,
                    SFMDocumentHistoryContract.DocumentState.withCaret(
                            before, SFMDocumentHistoryContract.codePointLength(before)),
                    SFMDocumentHistoryContract.DocumentState.withCaret(
                            after, SFMDocumentHistoryContract.codePointLength(after)),
                    Optional.of(text),
                    tick,
                    "type " + text
            );
        }

        @Override
        public void close() {
            if (opened) panel.closed();
            registration.close();
            controller.close();
        }
    }

    private static final class ManualExecutor implements Executor {
        private final ArrayDeque<Runnable> queue = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            queue.addLast(command);
        }

        private void drain() {
            while (!queue.isEmpty()) queue.removeFirst().run();
        }
    }
}
