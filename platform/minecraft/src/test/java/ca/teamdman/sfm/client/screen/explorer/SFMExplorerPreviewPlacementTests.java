package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.screen.text_editor.SFMTextDocumentPanelState;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelReopenRecipe;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMTestScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntentResult;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelMetadata;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceSide;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMExplorerPreviewPlacementTests {
    @Test
    void reviewLensesShareOnePreviewAreaButExplicitSplitsAndUnrelatedReviewsRemainSeparate() {
        var workspace = new FakeWorkspace(new SFMTestScreenPanel("changes"));
        var changes = workspace.focusedPanelId();
        var comments = workspace.add(new SFMTestScreenPanel("comments"), SFMWorkspacePanelMetadata.ordinary());
        String owner = ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewExplorerRuntime.previewOwner(
                java.nio.file.Path.of("review.sfm-review.json"));
        assertEquals(owner, ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewExplorerRuntime.previewOwner(
                java.nio.file.Path.of("child/../review.sfm-review.json")));
        var source = SFMExplorerPreviewPlacement.place(workspace, changes, owner,
                SFMExplorerPreviewPlacement.Mode.FOCUS_PREVIEW, editor("source", true), null, Optional.of("source|sha"));
        var value = SFMExplorerPreviewPlacement.place(workspace, comments, owner,
                SFMExplorerPreviewPlacement.Mode.FOCUS_PREVIEW, editor("comment value", true), null, Optional.of("comment|sha"));
        var sourceAgain = SFMExplorerPreviewPlacement.place(workspace, changes, owner,
                SFMExplorerPreviewPlacement.Mode.FOCUS_PREVIEW, editor("duplicate", true), null, Optional.of("source|sha"));
        assertTrue(value.reusedOwnedPreview());
        assertEquals(source.openedPanelId(), sourceAgain.openedPanelId());
        assertEquals(1, workspace.sideOpens, "first preview creates the sole preview area");
        assertEquals(1, workspace.slotOpens, "other lens opens its value as a tab in that area");
        SFMExplorerPreviewPlacement.place(workspace, comments, owner, SFMExplorerPreviewPlacement.Mode.ADJACENT,
                editor("explicit adjacent", true), null, Optional.of("source|sha"));
        assertEquals(2, workspace.sideOpens);
        String otherOwner = ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewExplorerRuntime.previewOwner(
                java.nio.file.Path.of("other.sfm-review.json"));
        assertFalse(owner.equals(otherOwner));
        SFMExplorerPreviewPlacement.place(workspace, changes, otherOwner, SFMExplorerPreviewPlacement.Mode.FOCUS_PREVIEW,
                editor("unrelated review", true), null, Optional.of("source|sha"));
        assertEquals(3, workspace.sideOpens, "different review cannot consume this one's preview area");
    }

    @Test
    void previewRetainsExplorerFocusAndFocusModeReplacesOnlyItsOwnedReadOnlyPreview() {
        SFMTestScreenPanel explorer = new SFMTestScreenPanel("explorer");
        FakeWorkspace workspace = new FakeWorkspace(explorer);
        SFMWorkspacePanelId explorerPanelId = workspace.focusedPanelId();
        TestDocumentPanel first = editor("first", true);

        var preview = SFMExplorerPreviewPlacement.place(
                workspace,
                explorerPanelId,
                "explorer-1",
                SFMExplorerPreviewPlacement.Mode.PREVIEW,
                first,
                null
        );

        assertTrue(preview.applied());
        assertEquals(explorerPanelId, workspace.focusedPanelId());
        assertEquals(2, workspace.panelIds().size());
        assertTrue(workspace.panelMetadata(preview.openedPanelId())
                .isExplorerPreviewOwnedBy("explorer-1"));

        TestDocumentPanel second = editor("second", true);
        var focused = SFMExplorerPreviewPlacement.place(
                workspace,
                explorerPanelId,
                "explorer-1",
                SFMExplorerPreviewPlacement.Mode.FOCUS_PREVIEW,
                second,
                null
        );

        assertTrue(focused.applied());
        assertTrue(focused.reusedOwnedPreview());
        assertEquals(focused.openedPanelId(), workspace.focusedPanelId());
        assertEquals(2, workspace.panelIds().size());
        assertFalse(workspace.containsPanel(preview.openedPanelId()));
        assertSame(second, workspace.panelInstance(focused.openedPanelId()));
    }

    @Test
    void writableOrUnrelatedPanelsAreNeverPreviewReplacementCandidates() {
        SFMTestScreenPanel explorer = new SFMTestScreenPanel("explorer");
        FakeWorkspace workspace = new FakeWorkspace(explorer);
        SFMWorkspacePanelId explorerPanelId = workspace.focusedPanelId();
        TestDocumentPanel writable = editor("writable", false);
        workspace.openToSide(
                explorerPanelId,
                SFMWorkspaceSide.RIGHT,
                writable,
                SFMWorkspacePanelMetadata.explorerPreview("explorer-1"),
                null
        );
        SFMWorkspacePanelId writableId = workspace.focusedPanelId();

        var result = SFMExplorerPreviewPlacement.place(
                workspace,
                explorerPanelId,
                "explorer-1",
                SFMExplorerPreviewPlacement.Mode.PREVIEW,
                editor("preview", true),
                null
        );

        assertTrue(result.applied());
        assertFalse(result.reusedOwnedPreview());
        assertTrue(workspace.containsPanel(writableId));
        assertSame(writable, workspace.panelInstance(writableId));
        assertEquals(3, workspace.panelIds().size());
    }

    @Test
    void adjacentModeCreatesAnOrdinaryVisiblePanelInsteadOfConsumingThePreview() {
        SFMTestScreenPanel explorer = new SFMTestScreenPanel("explorer");
        FakeWorkspace workspace = new FakeWorkspace(explorer);
        SFMWorkspacePanelId explorerPanelId = workspace.focusedPanelId();
        TestDocumentPanel adjacent = editor("adjacent", true);

        var result = SFMExplorerPreviewPlacement.place(
                workspace,
                explorerPanelId,
                "explorer-1",
                SFMExplorerPreviewPlacement.Mode.ADJACENT,
                adjacent,
                null
        );

        assertTrue(result.applied());
        assertEquals(result.openedPanelId(), workspace.focusedPanelId());
        assertEquals(2, workspace.panelIds().size());
        assertEquals(SFMWorkspacePanelMetadata.ordinary(), workspace.panelMetadata(result.openedPanelId()));
    }

    @Test
    void typedBeforeAfterPreviewsRemainTwoStableEntriesAndRevisitingFocusesExisting() {
        SFMTestScreenPanel explorer = new SFMTestScreenPanel("explorer");
        FakeWorkspace workspace = new FakeWorkspace(explorer);
        SFMWorkspacePanelId explorerPanelId = workspace.focusedPanelId();

        var before = SFMExplorerPreviewPlacement.place(
                workspace, explorerPanelId, "review-explorer",
                SFMExplorerPreviewPlacement.Mode.FOCUS_PREVIEW,
                editor("before", true), null, Optional.of("review|before|sha-a")
        );
        var after = SFMExplorerPreviewPlacement.place(
                workspace, explorerPanelId, "review-explorer",
                SFMExplorerPreviewPlacement.Mode.FOCUS_PREVIEW,
                editor("after", true), null, Optional.of("review|after|sha-b")
        );
        var beforeAgain = SFMExplorerPreviewPlacement.place(
                workspace, explorerPanelId, "review-explorer",
                SFMExplorerPreviewPlacement.Mode.FOCUS_PREVIEW,
                editor("duplicate-before-must-not-open", true), null, Optional.of("review|before|sha-a")
        );

        assertTrue(before.applied());
        assertTrue(after.applied());
        assertTrue(beforeAgain.applied());
        assertEquals(3, workspace.panelIds().size(), "explorer plus exactly two typed review entries");
        assertEquals(before.openedPanelId(), beforeAgain.openedPanelId());
        assertEquals(before.openedPanelId(), workspace.focusedPanelId());
        assertTrue(workspace.containsPanel(after.openedPanelId()));
    }

    private static TestDocumentPanel editor(String title, boolean readOnly) {
        return new TestDocumentPanel(title, readOnly);
    }

    @Test
    void revisitingAnInactiveTypedPreviewActivatesItsTabBeforeReturningFocusToExplorer() {
        var workspace = new FakeWorkspace(new SFMTestScreenPanel("explorer"));
        var explorer = workspace.focusedPanelId();
        var before = SFMExplorerPreviewPlacement.place(workspace, explorer, "review",
                SFMExplorerPreviewPlacement.Mode.PREVIEW, editor("before", true), null, Optional.of("before"));
        SFMExplorerPreviewPlacement.place(workspace, explorer, "review",
                SFMExplorerPreviewPlacement.Mode.PREVIEW, editor("after", true), null, Optional.of("after"));
        workspace.focusHistory.clear();
        var again = SFMExplorerPreviewPlacement.place(workspace, explorer, "review",
                SFMExplorerPreviewPlacement.Mode.PREVIEW, editor("duplicate", true), null, Optional.of("before"));
        assertTrue(again.applied());
        assertEquals(List.of(before.openedPanelId(), explorer), workspace.focusHistory);
        assertEquals(explorer, workspace.focusedPanelId());
        assertEquals(3, workspace.panelIds().size());
    }

    private record TestDocumentPanel(String name, boolean readOnly)
            implements SFMScreenPanel, SFMTextDocumentPanelState {
        @Override public Component title() {
            return Component.literal(name);
        }

        @Override public void render(
                PoseStack poseStack,
                Minecraft minecraft,
                SFMScreenPanelBounds bounds,
                int mouseX,
                int mouseY,
                float partialTick,
                boolean focused
        ) {
        }

        @Override public boolean isReadOnly() {
            return readOnly;
        }

        @Override public Optional<SFMTextDocumentSnapshot> documentSnapshot() {
            return Optional.empty();
        }
    }

    private static final class FakeWorkspace implements SFMExplorerPreviewPlacement.Workspace {
        private record Entry(SFMScreenPanel panel, SFMWorkspacePanelMetadata metadata) {
        }

        private final Map<SFMWorkspacePanelId, Entry> entries = new LinkedHashMap<>();
        private long nextId = 1;
        private SFMWorkspacePanelId focused;
        private int sideOpens;
        private int slotOpens;
        private final java.util.ArrayList<SFMWorkspacePanelId> focusHistory = new java.util.ArrayList<>();

        private FakeWorkspace(SFMScreenPanel initial) {
            focused = add(initial, SFMWorkspacePanelMetadata.ordinary());
        }

        @Override public boolean containsPanel(SFMWorkspacePanelId panelId) {
            return entries.containsKey(panelId);
        }

        @Override public SFMWorkspacePanelIntentResult openToSide(
                SFMWorkspacePanelId source,
                SFMWorkspaceSide side,
                SFMScreenPanel panel,
                SFMWorkspacePanelMetadata metadata,
                SFMPanelReopenRecipe reopenRecipe
        ) {
            if (!entries.containsKey(source)) return SFMWorkspacePanelIntentResult.UNAVAILABLE;
            sideOpens++;
            focused = add(panel, metadata);
            return SFMWorkspacePanelIntentResult.APPLIED;
        }

        @Override public SFMWorkspacePanelIntentResult openIntoSlot(
                SFMWorkspacePanelId slot,
                SFMScreenPanel panel,
                SFMWorkspacePanelMetadata metadata,
                SFMPanelReopenRecipe reopenRecipe
        ) {
            if (!entries.containsKey(slot)) return SFMWorkspacePanelIntentResult.UNAVAILABLE;
            slotOpens++;
            focused = add(panel, metadata);
            return SFMWorkspacePanelIntentResult.APPLIED;
        }

        @Override public SFMWorkspacePanelIntentResult closePanel(SFMWorkspacePanelId panelId) {
            if (entries.remove(panelId) == null) return SFMWorkspacePanelIntentResult.UNAVAILABLE;
            if (panelId.equals(focused)) focused = entries.keySet().iterator().next();
            return SFMWorkspacePanelIntentResult.APPLIED;
        }

        @Override public boolean focusPanel(SFMWorkspacePanelId panelId) {
            if (!entries.containsKey(panelId)) return false;
            focusHistory.add(panelId);
            focused = panelId;
            return true;
        }

        @Override public SFMWorkspacePanelId focusedPanelId() {
            return focused;
        }

        @Override public List<SFMWorkspacePanelId> panelIds() {
            return List.copyOf(entries.keySet());
        }

        @Override public SFMWorkspacePanelMetadata panelMetadata(SFMWorkspacePanelId panelId) {
            Entry entry = entries.get(panelId);
            return entry == null ? null : entry.metadata();
        }

        @Override public SFMScreenPanel panelInstance(SFMWorkspacePanelId panelId) {
            Entry entry = entries.get(panelId);
            return entry == null ? null : entry.panel();
        }

        private SFMWorkspacePanelId add(SFMScreenPanel panel, SFMWorkspacePanelMetadata metadata) {
            SFMWorkspacePanelId id = new SFMWorkspacePanelId(nextId++);
            entries.put(id, new Entry(panel, metadata));
            return id;
        }
    }
}
