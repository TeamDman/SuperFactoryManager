package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewExplorerRuntime;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanelModel;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanelViewport;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelEntryAffordanceLayout;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelMetadata;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetArtifactFormat;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Mouse-only natural proof for the generic release-review Explorer and pane lifecycle. */
public final class ExerciseReleaseReviewExplorerUxPuppetAction implements SFMPuppetAction {
    private static final String READ_ONLY_VIEW_PREFIX =
            "sfm action invoke sfm:review/session/open/read_only/view ";
    private static final String ENTRY_FOCUS_PREFIX =
            "sfm action invoke sfm:panel/entry/focus ";
    private static final String PANE_CLOSE = "sfm action invoke sfm:pane/close";
    private static final String PANE_CLOSE_CONFIRM_PREFIX =
            "sfm action invoke sfm:pane/close/confirm ";

    private final SFMPath reviewFile;
    private Stage stage = Stage.OPEN_FILE_CONTEXT;
    private int stageTicks;
    private String reviewExplorerId;
    private SFMPath beforePath;
    private SFMPath afterPath;
    private SFMWorkspacePanelId beforePanelId;
    private SFMWorkspacePanelId afterPanelId;
    private String beforeIdentity;
    private String afterIdentity;
    private String removedIdentity;
    private final List<String> entryMenuCommands = new ArrayList<>();

    public ExerciseReleaseReviewExplorerUxPuppetAction(Path reviewFile) {
        this.reviewFile = SFMPath.fromNative(Objects.requireNonNull(reviewFile, "reviewFile"));
    }

    @Override
    public String description() {
        return "exercise mouse-only release-review open, before/after dedup, reveal, tabs, and pane close";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        return switch (stage) {
            case OPEN_FILE_CONTEXT -> openFileContext();
            case CHOOSE_READ_ONLY_VIEW -> chooseReadOnlyView(runtime);
            case WAIT_REVIEW_EXPLORER -> waitReviewExplorer();
            case EXPAND_CHANGES -> expandChanges();
            case OPEN_BEFORE -> openLeaf(beforePath, Stage.WAIT_BEFORE);
            case WAIT_BEFORE -> waitBefore();
            case OPEN_AFTER -> openLeaf(afterPath, Stage.WAIT_AFTER);
            case WAIT_AFTER -> waitAfter();
            case REOPEN_BEFORE -> openLeaf(beforePath, Stage.WAIT_DEDUP);
            case WAIT_DEDUP -> waitDedup();
            case SELECT_REVEAL_DECOY -> selectRevealDecoy();
            case WAIT_REVEAL_DECOY -> waitRevealDecoy();
            case CLICK_REVEAL -> clickReveal();
            case WAIT_REVEAL -> waitReveal();
            case OPEN_ENTRY_MENU -> openEntryMenu();
            case CHOOSE_ENTRY_FOCUS -> chooseEntryFocus(runtime);
            case WAIT_ENTRY_MENU_CLOSE -> waitEntryMenuClose();
            case LEFT_FOCUS_ENTRY -> leftFocusEntry();
            case MIDDLE_CLOSE_ENTRY -> middleCloseEntry();
            case WAIT_SINGLE_ENTRY -> waitSingleEntry();
            case REOPEN_REMOVED_ENTRY -> reopenRemovedEntry();
            case WAIT_RESTACK -> waitRestack();
            case OPEN_PANE_MENU -> openPaneMenu();
            case CHOOSE_PANE_CLOSE -> choosePaneClose(runtime);
            case CONFIRM_PANE_CLOSE -> confirmPaneClose(runtime);
            case WAIT_PANE_CLOSED -> waitPaneClosed(runtime);
        };
    }

    private boolean openFileContext() {
        Handle explorer = explorer(false);
        if (explorer == null) return waitOrFail("the initial file Explorer");
        if (explorer.state().projection().rows().stream()
                .noneMatch(row -> row.path().equals(reviewFile))) {
            Optional<ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection.Row> collapsed =
                    explorer.state().projection().rows().stream()
                            .filter(row -> row.entry().expandable())
                            .filter(row -> !explorer.state().session().expanded().contains(row.path()))
                            .findFirst();
            if (collapsed.isPresent() && !clickChevron(explorer, collapsed.orElseThrow().path())) {
                return waitOrFail("the staged review parent root to enter the viewport");
            }
            return waitOrFail("the staged .sfm-review.json row to materialize");
        }
        if (!clickRow(explorer, reviewFile, GLFW.GLFW_MOUSE_BUTTON_RIGHT, false)) {
            return waitOrFail("the staged .sfm-review.json row");
        }
        transition(Stage.CHOOSE_READ_ONLY_VIEW);
        return false;
    }

    private boolean chooseReadOnlyView(ISFMGamePuppetRuntime runtime) {
        if (!(Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette)) {
            return waitOrFail("the review-file context palette");
        }
        String command = palette.choiceCommandsForAutomation().stream()
                .filter(candidate -> candidate.startsWith(READ_ONLY_VIEW_PREFIX))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "Review-file context menu omitted the read-only view action: "
                                + palette.choiceCommandsForAutomation()));
        if (!palette.choiceReadyForPointerAutomation(command)) {
            return waitOrFail("the read-only review choice to become pointer-ready");
        }
        runtime.clickActionChoice(command);
        transition(Stage.WAIT_REVIEW_EXPLORER);
        return false;
    }

    private boolean waitReviewExplorer() {
        Handle explorer = explorer(true);
        if (explorer == null) return waitOrFail("the generic release-review Explorer");
        reviewExplorerId = explorer.panel().explorerId().value();
        transition(Stage.EXPAND_CHANGES);
        return false;
    }

    private boolean expandChanges() {
        Handle explorer = explorer(true);
        if (explorer == null) return waitOrFail("the release-review Explorer while expanding Changes");
        List<ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection.Row> sourceRows =
                explorer.state().projection().rows().stream()
                        .filter(row -> SFMReleaseReviewExplorerRuntime.get().documentTarget(row.path()).isPresent())
                        .toList();
        for (var before : sourceRows) {
            if (!before.entry().label().startsWith("before")) continue;
            Optional<ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection.Row> after = sourceRows.stream()
                    .filter(candidate -> candidate.entry().label().startsWith("after"))
                    .filter(candidate -> sameParent(before.path(), candidate.path()))
                    .findFirst();
            if (after.isPresent()) {
                beforePath = before.path();
                afterPath = after.orElseThrow().path();
                transition(Stage.OPEN_BEFORE);
                return false;
            }
        }

        Optional<ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection.Row> collapsed =
                explorer.state().projection().rows().stream()
                        .filter(row -> row.entry().expandable())
                        .filter(row -> !explorer.state().session().expanded().contains(row.path()))
                        .findFirst();
        if (collapsed.isEmpty()) return waitOrFail("a before/after pair in the Changes projection");
        if (!clickChevron(explorer, collapsed.orElseThrow().path())) {
            return waitOrFail("the next expandable Changes row to enter the viewport");
        }
        return false;
    }

    private boolean openLeaf(SFMPath path, Stage next) {
        Handle explorer = explorer(true);
        if (explorer == null) return waitOrFail("the release-review Explorer before opening a source leaf");
        if (!clickRow(explorer, path, GLFW.GLFW_MOUSE_BUTTON_LEFT, true)) {
            return waitOrFail("review source row " + path.canonical());
        }
        transition(next);
        return false;
    }

    private boolean waitBefore() {
        SFMScreenMultiplexer workspace = workspace();
        if (workspace == null) return waitOrFail("the before preview");
        List<Preview> previews = previews(workspace);
        if (previews.size() != 1) return waitOrFail("one typed before preview");
        Preview before = previews.get(0);
        beforePanelId = before.id();
        beforeIdentity = before.identity();
        require(workspace.focusedPanelId().equals(beforePanelId), "before preview did not receive focus");
        transition(Stage.OPEN_AFTER);
        return false;
    }

    private boolean waitAfter() {
        SFMScreenMultiplexer workspace = workspace();
        if (workspace == null) return waitOrFail("the after preview");
        List<Preview> previews = previews(workspace);
        if (previews.size() != 2) return waitOrFail("two typed before/after previews");
        Preview after = previews.stream().filter(value -> !value.id().equals(beforePanelId))
                .findFirst().orElseThrow();
        afterPanelId = after.id();
        afterIdentity = after.identity();
        require(!beforeIdentity.equals(afterIdentity), "before and after reused one presentation identity");
        require(workspace.focusedPanelId().equals(afterPanelId), "after preview did not receive focus");
        transition(Stage.REOPEN_BEFORE);
        return false;
    }

    private boolean waitDedup() {
        SFMScreenMultiplexer workspace = workspace();
        if (workspace == null) return waitOrFail("the deduplicated before preview");
        List<Preview> previews = previews(workspace);
        require(previews.size() == 2, "A/B/A browsing grew beyond two typed preview entries");
        require(workspace.focusedPanelId().equals(beforePanelId),
                "reopening before did not focus its exact existing entry");
        transition(Stage.SELECT_REVEAL_DECOY);
        return false;
    }

    private boolean selectRevealDecoy() {
        Handle explorer = explorer(true);
        if (explorer == null) return waitOrFail("the release-review Explorer before selecting a reveal decoy");
        if (!clickRow(explorer, afterPath, GLFW.GLFW_MOUSE_BUTTON_LEFT, false)) {
            return waitOrFail("the after row used as a reveal decoy");
        }
        transition(Stage.WAIT_REVEAL_DECOY);
        return false;
    }

    private boolean waitRevealDecoy() {
        Handle explorer = explorer(true);
        if (explorer == null) return waitOrFail("the release-review Explorer after selecting a reveal decoy");
        if (!explorer.state().selectedPath().equals(Optional.of(afterPath))) {
            return waitOrFail("the after row to become the selected reveal decoy");
        }
        require(previews(explorer.workspace()).size() == 2,
                "selecting the reveal decoy changed the typed preview stack");
        transition(Stage.CLICK_REVEAL);
        return false;
    }

    private boolean clickReveal() {
        Handle explorer = explorer(true);
        if (explorer == null) return waitOrFail("the target-block reveal control");
        double[] point = workspacePoint(explorer, explorer.state().viewport().layout().revealControl());
        require(explorer.workspace().mouseClicked(point[0], point[1], GLFW.GLFW_MOUSE_BUTTON_LEFT),
                "target-block reveal click was not handled");
        transition(Stage.WAIT_REVEAL);
        return false;
    }

    private boolean waitReveal() {
        Handle explorer = explorer(true);
        if (explorer == null) return waitOrFail("the review Explorer after target reveal");
        if (!explorer.state().selectedPath().equals(Optional.of(beforePath))) {
            return waitOrFail("the exact before row to be revealed");
        }
        require(previews(explorer.workspace()).size() == 2, "reveal changed the typed preview stack");
        transition(Stage.OPEN_ENTRY_MENU);
        return false;
    }

    private boolean openEntryMenu() {
        SFMScreenMultiplexer workspace = workspace();
        if (workspace == null) return waitOrFail("the numbered preview entries");
        List<SFMPanelEntryAffordanceLayout.HitRegion> hits = previewHits(workspace);
        if (hits.size() != 2) return waitOrFail("two rendered numbered preview entries");
        clickHit(workspace, hits.get(0), GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        transition(Stage.CHOOSE_ENTRY_FOCUS);
        return false;
    }

    private boolean chooseEntryFocus(ISFMGamePuppetRuntime runtime) {
        if (!(Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette)) {
            return waitOrFail("the exact numbered-entry action palette");
        }
        entryMenuCommands.clear();
        entryMenuCommands.addAll(palette.choiceCommandsForAutomation());
        require(entryMenuCommands.stream().anyMatch(command -> command.startsWith(ENTRY_FOCUS_PREFIX)),
                "numbered-entry menu omitted exact focus");
        require(entryMenuCommands.contains(PANE_CLOSE), "numbered-entry menu omitted pane close");
        require(entryMenuCommands.stream().anyMatch(command -> command.contains("sfm:panel/entry/close")),
                "numbered-entry menu omitted exact close");
        require(entryMenuCommands.stream().filter(command -> command.contains("sfm:panel/entry/move/")).count() == 4,
                "numbered-entry menu omitted a directional move");
        String focus = entryMenuCommands.stream().filter(command -> command.startsWith(ENTRY_FOCUS_PREFIX))
                .findFirst().orElseThrow();
        if (!palette.choiceReadyForPointerAutomation(focus)) {
            return waitOrFail("the exact entry-focus choice to become pointer-ready");
        }
        runtime.clickActionChoice(focus);
        transition(Stage.WAIT_ENTRY_MENU_CLOSE);
        return false;
    }

    private boolean waitEntryMenuClose() {
        if (workspace() == null) return waitOrFail("the workspace after the entry action palette");
        transition(Stage.LEFT_FOCUS_ENTRY);
        return false;
    }

    private boolean leftFocusEntry() {
        SFMScreenMultiplexer workspace = workspace();
        if (workspace == null) return waitOrFail("the numbered entries before left-click focus");
        List<SFMPanelEntryAffordanceLayout.HitRegion> hits = previewHits(workspace);
        if (hits.size() != 2) return waitOrFail("two numbered entries before left-click focus");
        SFMPanelEntryAffordanceLayout.HitRegion target = hits.stream()
                .filter(hit -> !hit.entryId().equals(workspace.focusedPanelId()))
                .findFirst().orElse(hits.get(0));
        clickHit(workspace, target, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        require(workspace.focusedPanelId().equals(target.entryId()), "left-click did not focus the exact entry");
        transition(Stage.MIDDLE_CLOSE_ENTRY);
        return false;
    }

    private boolean middleCloseEntry() {
        SFMScreenMultiplexer workspace = workspace();
        if (workspace == null) return waitOrFail("the focused numbered entry before middle-click close");
        List<SFMPanelEntryAffordanceLayout.HitRegion> hits = previewHits(workspace);
        if (hits.size() != 2) return waitOrFail("fresh numbered-entry hit regions after focus");
        SFMPanelEntryAffordanceLayout.HitRegion target = hits.stream()
                .filter(hit -> hit.entryId().equals(workspace.focusedPanelId()))
                .findFirst().orElseThrow();
        removedIdentity = Objects.requireNonNull(workspace.panelMetadata(target.entryId()), "entry metadata")
                .explorerPreviewPresentationIdentity().orElseThrow();
        clickHit(workspace, target, GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
        transition(Stage.WAIT_SINGLE_ENTRY);
        return false;
    }

    private boolean waitSingleEntry() {
        SFMScreenMultiplexer workspace = workspace();
        if (workspace == null) return waitOrFail("one preview after middle-click close");
        if (previews(workspace).size() != 1) return waitOrFail("middle-click to close exactly one entry");
        transition(Stage.REOPEN_REMOVED_ENTRY);
        return false;
    }

    private boolean reopenRemovedEntry() {
        SFMPath path = removedIdentity.equals(beforeIdentity) ? beforePath : afterPath;
        return openLeaf(path, Stage.WAIT_RESTACK);
    }

    private boolean waitRestack() {
        SFMScreenMultiplexer workspace = workspace();
        if (workspace == null) return waitOrFail("the restored two-entry preview pane");
        List<Preview> previews = previews(workspace);
        if (previews.size() != 2) return waitOrFail("the closed presentation to reopen as a second entry");
        require(previews.stream().map(Preview::identity).sorted().toList()
                        .equals(List.of(beforeIdentity, afterIdentity).stream().sorted().toList()),
                "reopened pane did not restore the exact before/after identities");
        transition(Stage.OPEN_PANE_MENU);
        return false;
    }

    private boolean openPaneMenu() {
        SFMScreenMultiplexer workspace = workspace();
        if (workspace == null) return waitOrFail("the restored numbered entries before pane close");
        List<SFMPanelEntryAffordanceLayout.HitRegion> hits = previewHits(workspace);
        if (hits.size() != 2) return waitOrFail("rendered restored numbered entries");
        clickHit(workspace, hits.get(0), GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        transition(Stage.CHOOSE_PANE_CLOSE);
        return false;
    }

    private boolean choosePaneClose(ISFMGamePuppetRuntime runtime) {
        if (!(Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette)) {
            return waitOrFail("the numbered-entry menu before pane close");
        }
        require(palette.choiceCommandsForAutomation().contains(PANE_CLOSE),
                "numbered-entry menu lost pane close on its second opening");
        if (!palette.choiceReadyForPointerAutomation(PANE_CLOSE)) {
            return waitOrFail("the pane-close choice to become pointer-ready");
        }
        runtime.clickActionChoice(PANE_CLOSE);
        transition(Stage.CONFIRM_PANE_CLOSE);
        return false;
    }

    private boolean confirmPaneClose(ISFMGamePuppetRuntime runtime) {
        if (!(Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette)) {
            return waitOrFail("the exact-count pane-close confirmation");
        }
        String confirm = palette.choiceCommandsForAutomation().stream()
                .filter(command -> command.startsWith(PANE_CLOSE_CONFIRM_PREFIX))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "Unsafe two-entry pane close did not request confirmation: "
                                + palette.choiceCommandsForAutomation()));
        require(palette.getTitle().getString().contains("total=2"),
                "pane-close confirmation omitted its exact total: " + palette.getTitle().getString());
        if (!palette.choiceReadyForPointerAutomation(confirm)) {
            return waitOrFail("the pane-close confirmation to become pointer-ready");
        }
        runtime.clickActionChoice(confirm);
        transition(Stage.WAIT_PANE_CLOSED);
        return false;
    }

    private boolean waitPaneClosed(ISFMGamePuppetRuntime runtime) {
        SFMScreenMultiplexer workspace = workspace();
        if (workspace == null) return waitOrFail("the workspace after pane close");
        if (!previews(workspace).isEmpty()) return waitOrFail("the complete preview pane to close");
        require(explorer(true) != null, "pane close removed the release-review Explorer");
        require(explorer(false) != null, "pane close removed the original file Explorer");

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("schema", "sfm.release-review-explorer-ux/1");
        evidence.put("review_file", reviewFile.canonical());
        evidence.put("review_explorer_id", reviewExplorerId);
        evidence.put("before_path", beforePath.canonical());
        evidence.put("after_path", afterPath.canonical());
        evidence.put("before_presentation_identity", beforeIdentity);
        evidence.put("after_presentation_identity", afterIdentity);
        evidence.put("bounded_preview_count_after_a_b_a", 2);
        evidence.put("target_revealed_path", beforePath.canonical());
        evidence.put("entry_menu_commands", List.copyOf(entryMenuCommands));
        evidence.put("middle_close_remaining_entries", 1);
        evidence.put("pane_close_remaining_entries", 0);
        runtime.writeArtifact(
                "release-review-explorer-ux",
                SFMGamePuppetArtifactFormat.JSON,
                new GsonBuilder().setPrettyPrinting().create().toJson(evidence)
        );
        return true;
    }

    private Handle explorer(boolean review) {
        SFMScreenMultiplexer workspace = workspace();
        if (workspace == null) return null;
        for (SFMWorkspacePanelId id : workspace.panelIds()) {
            if (!(workspace.panelInstance(id) instanceof SFMExplorerPanel panel)) continue;
            boolean isReview = panel.sessionSnapshot().roots().stream()
                    .anyMatch(root -> root.scheme().equals(SFMReleaseReviewExplorerRuntime.PATH_SCHEME));
            if (isReview != review) continue;
            SFMScreenPanelBounds bounds = contentBounds(workspace, id);
            return new Handle(workspace, id, panel, bounds, panel.model().state(bounds));
        }
        return null;
    }

    private static SFMScreenPanelBounds contentBounds(
            SFMScreenMultiplexer workspace,
            SFMWorkspacePanelId panelId
    ) {
        SFMScreenPanelBounds bounds = workspace.panelContentBounds(panelId);
        if (bounds == null) throw new IllegalStateException("Explorer has no content bounds");
        return bounds;
    }

    private static SFMScreenMultiplexer workspace() {
        return Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace ? workspace : null;
    }

    private boolean clickChevron(Handle handle, SFMPath path) {
        SFMExplorerPanelViewport.Cell cell = visibleCell(handle, path);
        if (cell == null) return scrollToward(handle, path);
        double[] point = workspacePoint(handle, cell.chevron());
        require(handle.workspace().mouseClicked(point[0], point[1], GLFW.GLFW_MOUSE_BUTTON_LEFT),
                "Explorer chevron click was not handled");
        return true;
    }

    private boolean clickRow(Handle handle, SFMPath path, int button, boolean twice) {
        SFMExplorerPanelViewport.Cell cell = visibleCell(handle, path);
        if (cell == null) return scrollToward(handle, path);
        SFMExplorerPanelViewport.Rect bounds = cell.bounds();
        SFMExplorerPanelViewport.Rect target = new SFMExplorerPanelViewport.Rect(
                bounds.x() + Math.min(14, Math.max(0, bounds.width() - 1)),
                bounds.y(),
                Math.max(1, bounds.width() - Math.min(14, Math.max(0, bounds.width() - 1))),
                bounds.height()
        );
        double[] point = workspacePoint(handle, target);
        require(handle.workspace().mouseClicked(point[0], point[1], button),
                "Explorer row click was not handled for " + path.canonical());
        if (twice) {
            require(handle.workspace().mouseClicked(point[0], point[1], button),
                    "Explorer row double-click was not handled for " + path.canonical());
        }
        return true;
    }

    private static SFMExplorerPanelViewport.Cell visibleCell(Handle handle, SFMPath path) {
        return handle.state().viewport().cells().stream()
                .filter(cell -> cell.row().path().equals(path))
                .findFirst().orElse(null);
    }

    private static boolean scrollToward(Handle handle, SFMPath path) {
        List<ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection.Row> rows =
                handle.state().projection().rows();
        int index = -1;
        for (int candidate = 0; candidate < rows.size(); candidate++) {
            if (rows.get(candidate).path().equals(path)) {
                index = candidate;
                break;
            }
        }
        if (index < 0) return false;
        SFMExplorerPanelViewport.Snapshot viewport = handle.state().viewport();
        double delta = index < viewport.firstVisibleIndex() ? 1D : -1D;
        double[] point = workspacePoint(handle, viewport.layout().body());
        require(handle.workspace().mouseScrolled(point[0], point[1], delta),
                "Explorer wheel did not move toward " + path.canonical());
        return false;
    }

    private List<Preview> previews(SFMScreenMultiplexer workspace) {
        if (reviewExplorerId == null) return List.of();
        ArrayList<Preview> answer = new ArrayList<>();
        for (SFMWorkspacePanelId id : workspace.panelIds()) {
            SFMWorkspacePanelMetadata metadata = workspace.panelMetadata(id);
            if (metadata == null || !metadata.isExplorerPreviewOwnedBy(reviewExplorerId)) continue;
            answer.add(new Preview(
                    id,
                    metadata.explorerPreviewPresentationIdentity().orElseThrow()
            ));
        }
        return List.copyOf(answer);
    }

    private List<SFMPanelEntryAffordanceLayout.HitRegion> previewHits(SFMScreenMultiplexer workspace) {
        return workspace.panelEntryHitRegions().stream()
                .filter(hit -> {
                    SFMWorkspacePanelMetadata metadata = workspace.panelMetadata(hit.entryId());
                    return metadata != null && metadata.isExplorerPreviewOwnedBy(reviewExplorerId);
                })
                .toList();
    }

    private static void clickHit(
            SFMScreenMultiplexer workspace,
            SFMPanelEntryAffordanceLayout.HitRegion hit,
            int button
    ) {
        SFMScreenPanelBounds bounds = hit.bounds();
        require(workspace.mouseClicked(
                        bounds.x() + bounds.width() / 2D,
                        bounds.y() + bounds.height() / 2D,
                        button
                ),
                "Numbered entry did not handle mouse button " + button);
    }

    private static double[] workspacePoint(Handle handle, SFMExplorerPanelViewport.Rect local) {
        SFMScreenPanelBounds panel = handle.workspace().panelBounds(handle.panelId());
        require(panel != null, "Explorer panel lost its workspace allocation");
        SFMScreenPanelBounds physicalContent = panel.inset(1);
        double scaleX = physicalContent.width() / (double) Math.max(1, handle.bounds().width());
        double scaleY = physicalContent.height() / (double) Math.max(1, handle.bounds().height());
        return new double[]{
                physicalContent.x() + (local.x() + Math.max(0.5D, local.width() / 2D)) * scaleX,
                physicalContent.y() + (local.y() + Math.max(0.5D, local.height() / 2D)) * scaleY
        };
    }

    private static boolean sameParent(SFMPath first, SFMPath second) {
        if (!first.scheme().equals(second.scheme()) || !first.authority().equals(second.authority())) return false;
        if (first.segments().size() != second.segments().size() || first.segments().isEmpty()) return false;
        int parentSize = first.segments().size() - 1;
        return first.segments().subList(0, parentSize).equals(second.segments().subList(0, parentSize));
    }

    private void transition(Stage next) {
        stage = next;
        stageTicks = 0;
    }

    private boolean waitOrFail(String target) {
        if (++stageTicks > SFMGamePuppetHelper.SCREEN_TIMEOUT_TICKS) {
            throw new IllegalStateException("Timed out waiting for " + target + " during " + stage);
        }
        return false;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private enum Stage {
        OPEN_FILE_CONTEXT,
        CHOOSE_READ_ONLY_VIEW,
        WAIT_REVIEW_EXPLORER,
        EXPAND_CHANGES,
        OPEN_BEFORE,
        WAIT_BEFORE,
        OPEN_AFTER,
        WAIT_AFTER,
        REOPEN_BEFORE,
        WAIT_DEDUP,
        SELECT_REVEAL_DECOY,
        WAIT_REVEAL_DECOY,
        CLICK_REVEAL,
        WAIT_REVEAL,
        OPEN_ENTRY_MENU,
        CHOOSE_ENTRY_FOCUS,
        WAIT_ENTRY_MENU_CLOSE,
        LEFT_FOCUS_ENTRY,
        MIDDLE_CLOSE_ENTRY,
        WAIT_SINGLE_ENTRY,
        REOPEN_REMOVED_ENTRY,
        WAIT_RESTACK,
        OPEN_PANE_MENU,
        CHOOSE_PANE_CLOSE,
        CONFIRM_PANE_CLOSE,
        WAIT_PANE_CLOSED
    }

    private record Handle(
            SFMScreenMultiplexer workspace,
            SFMWorkspacePanelId panelId,
            SFMExplorerPanel panel,
            SFMScreenPanelBounds bounds,
            SFMExplorerPanelModel.State state
    ) {
    }

    private record Preview(SFMWorkspacePanelId id, String identity) {
    }
}
