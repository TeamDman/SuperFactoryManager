package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewRuntime;
import ca.teamdman.sfm.client.screen.workspace.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SFMReleaseReviewNavigationGuardTests {
    @TempDir Path directory;

    @Test
    void exactReviewAndBothPanelInstancesRemainAuthorityWhileFocusAndTabsMayChange() throws Exception {
        Path file = fixture();
        try (var runtime = new SFMReleaseReviewRuntime()) {
            runtime.open(file, false);
            var original = runtime.snapshot();
            var layout = SFMWorkspaceLayout.sideBySide(new SFMTestScreenPanel("source"), new SFMTestScreenPanel("comments"));
            var workspace = SFMHeadlessWorkspaceTestSupport.create(layout);
            var ids = workspace.panelIds();
            var roots = Set.of(SFMPath.fromNative(file));
            var guard = new SFMReleaseReviewNavigationGuard(
                    SFMClientActionContinuation.capture(new SFMClientActionContext(workspace, () -> true, ids.get(0))),
                    SFMClientActionContinuation.capture(new SFMClientActionContext(workspace, () -> true, ids.get(1))),
                    file, original.openEpoch(), original.generation(), roots);
            assertTrue(guard.matches(workspace, original, roots));
            layout.pushToStack(ids.get(1), new SFMTestScreenPanel("another tab"), SFMWorkspacePanelMetadata.ordinary());
            assertTrue(guard.matches(workspace, original, roots), "a still-attached hidden tab retains identity");
            assertFalse(guard.matches(workspace, original, Set.of(SFMPath.fromNative(directory.resolve("other-root")))));
            var otherPath = new SFMReleaseReviewRuntime.Snapshot(Optional.of(directory.resolve("other-review")),
                    original.document(), original.access(), original.generation(), original.openEpoch(), false);
            assertFalse(guard.matches(workspace, otherPath, roots));
            var nextGeneration = new SFMReleaseReviewRuntime.Snapshot(original.path(), original.document(), original.access(),
                    original.generation() + 1, original.openEpoch(), false);
            assertFalse(guard.matches(workspace, nextGeneration, roots));
            runtime.openAsync(file, false).join();
            assertFalse(guard.matches(workspace, runtime.snapshot(), roots), "same path reopened is a new authority");
            assertFalse(guard.matches(SFMHeadlessWorkspaceTestSupport.create(SFMWorkspaceLayout.single(
                    new SFMTestScreenPanel("new workspace"))), original, roots));

            AtomicInteger published = new AtomicInteger();
            var delayed = new CompletableFuture<Void>();
            delayed.thenRun(() -> { if (guard.matches(workspace, original, roots)) published.incrementAndGet(); });
            layout.remove(ids.get(0));
            delayed.complete(null);
            assertEquals(0, published.get(), "a closed initiating editor cannot open a delayed target");
        }
    }

    @Test
    void replacementWithTheSameNumericIdsDoesNotReauthorizeAnOldCallback() throws Exception {
        Path file = fixture();
        try (var runtime = new SFMReleaseReviewRuntime()) {
            runtime.open(file, false);
            var original = runtime.snapshot();
            var workspace = SFMHeadlessWorkspaceTestSupport.create(SFMWorkspaceLayout.sideBySide(
                    new SFMTestScreenPanel("source"), new SFMTestScreenPanel("comments")));
            var roots = Set.of(SFMPath.fromNative(file));
            var ids = workspace.panelIds();
            var guard = new SFMReleaseReviewNavigationGuard(
                    SFMClientActionContinuation.capture(new SFMClientActionContext(workspace, () -> true, ids.get(0))),
                    SFMClientActionContinuation.capture(new SFMClientActionContext(workspace, () -> true, ids.get(1))),
                    file, original.openEpoch(), original.generation(), roots);
            var replacement = SFMWorkspaceLayout.sideBySide(guard.origin().panel(),
                    new SFMTestScreenPanel("new comments"));
            var field = SFMScreenMultiplexer.class.getDeclaredField("layout");
            field.setAccessible(true);
            field.set(workspace, replacement);
            assertEquals(ids, workspace.panelIds());
            assertFalse(guard.matches(workspace, original, roots));
        }
    }

    private Path fixture() throws Exception {
        Path cursor = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 8 && cursor != null; depth++, cursor = cursor.getParent()) {
            Path fixture = cursor.resolve("docs/architecture/fixtures/release-review-v1.json");
            if (Files.isRegularFile(fixture)) return Files.copy(fixture, directory.resolve("review.sfm-review.json"));
        }
        throw new IllegalStateException("Canonical review fixture unavailable");
    }
}
