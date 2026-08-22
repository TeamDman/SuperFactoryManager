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
            panel.opened(
                    null,
                    new SFMScreenPanelBounds(0, 0, 640, 360),
                    SFMWorkspacePanelContext.unhosted(new SFMWorkspacePanelId(1))
            );
            opened = true;
            panel.tick();
        }

        private void openReady() {
            open();
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
