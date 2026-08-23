package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewKernel;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewExplorerRuntime;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewRuntime;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewSurfaceRuntime;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewSurfaceV1;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1Codec;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanelModel;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanelViewport;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelEntryAffordanceLayout;
import ca.teamdman.sfm.client.screen.workspace.SFMReleaseReviewExplorerScreenType;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelMetadata;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import ca.teamdman.sfm.client.context.SFMContextTextCoordinates;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetArtifactFormat;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetPointer;
import com.google.gson.GsonBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Mouse-only natural proof for the generic release-review Explorer and pane lifecycle. */
public final class ExerciseReleaseReviewExplorerUxPuppetAction implements SFMPuppetAction {
    private static final String WRITABLE_VIEW_PREFIX =
            "sfm action invoke sfm:review/session/open/view ";
    private static final String COMMENT_CHOICE_PREFIX =
            "sfm action invoke sfm:review/comment/choice/open ";
    private static final String COMMENT_APPROVE_PREFIX =
            "sfm action invoke sfm:review/comment/choice/apply ";
    private static final String LENS_SET_PREFIX =
            "sfm action invoke sfm:review/lens/set ";
    private static final String ENTRY_FOCUS_PREFIX =
            "sfm action invoke sfm:panel/entry/focus ";
    private static final String PANE_CLOSE = "sfm action invoke sfm:pane/close";
    private static final String PANE_CLOSE_CONFIRM_PREFIX =
            "sfm action invoke sfm:pane/close/confirm ";
    private static final int CAFE_REVIEW_START = 35;
    private static final int CAFE_REVIEW_END = 60;
    private static final List<SFMReleaseReviewExplorerScreenType.Projection> LENS_JOURNEY = List.of(
            SFMReleaseReviewExplorerScreenType.Projection.COMMENTS,
            SFMReleaseReviewExplorerScreenType.Projection.HASHTAGS,
            SFMReleaseReviewExplorerScreenType.Projection.QUERY,
            SFMReleaseReviewExplorerScreenType.Projection.STATUS,
            SFMReleaseReviewExplorerScreenType.Projection.MIGRATIONS,
            SFMReleaseReviewExplorerScreenType.Projection.CHANGES
    );

    private final SFMPath reviewFile;
    private Stage stage = Stage.OPEN_FILE_CONTEXT;
    private int stageTicks;
    private String reviewExplorerId;
    private SFMPath beforePath;
    private SFMPath afterPath;
    private SFMPath textDiffPath;
    private SFMPath structuredDiffPath;
    private SFMWorkspacePanelId beforePanelId;
    private SFMWorkspacePanelId afterPanelId;
    private SFMWorkspacePanelId textDiffPanelId;
    private SFMWorkspacePanelId structuredDiffPanelId;
    private String beforeIdentity;
    private String afterIdentity;
    private String removedIdentity;
    private int initialCommentCount;
    private String createdCommentId;
    private String createdCommentText;
    private C11SourceNavigationPuppetProbe.Pointer selectionEnd;
    private C11SourceNavigationPuppetProbe.Pointer selectionContext;
    private int nextLensIndex;
    private final List<String> lensMenuCommands = new ArrayList<>();
    private final List<String> visitedLenses = new ArrayList<>();
    private final List<String> entryMenuCommands = new ArrayList<>();

    public ExerciseReleaseReviewExplorerUxPuppetAction(Path reviewFile) {
        this.reviewFile = SFMPath.fromNative(Objects.requireNonNull(reviewFile, "reviewFile"));
    }

    @Override
    public String description() {
        return "exercise mouse-only release-review open, lazy diffs, persistent comments, lenses, tabs, and pane close";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        return switch (stage) {
            case OPEN_FILE_CONTEXT -> openFileContext();
            case CHOOSE_WRITABLE_VIEW -> chooseWritableView(runtime);
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
            case OPEN_TEXT_DIFF -> openLeaf(textDiffPath, Stage.WAIT_TEXT_DIFF);
            case WAIT_TEXT_DIFF -> waitDiff(SFMReleaseReviewSurfaceV1.SurfaceKind.TEXT_DIFF);
            case OPEN_STRUCTURED_DIFF -> openLeaf(structuredDiffPath, Stage.WAIT_STRUCTURED_DIFF);
            case WAIT_STRUCTURED_DIFF -> waitDiff(SFMReleaseReviewSurfaceV1.SurfaceKind.JAVA_STRUCTURED_DIFF);
            case REOPEN_TEXT_DIFF -> openLeaf(textDiffPath, Stage.WAIT_TEXT_DIFF_DEDUP);
            case WAIT_TEXT_DIFF_DEDUP -> waitTextDiffDedup();
            case CAPTURE_TEXT_DIFF -> captureTextDiff(runtime);
            case SELECT_DIFF_TEXT -> selectDiffText();
            case OPEN_COMMENT_MENU -> openCommentMenu();
            case CHOOSE_COMMENT_ROUTE -> chooseCommentRoute(runtime);
            case CHOOSE_APPROVED -> chooseApproved(runtime);
            case WAIT_COMMENT_SAVED -> waitCommentSaved();
            case OPEN_LENS_MENU -> openLensMenu();
            case CHOOSE_LENS -> chooseLens(runtime);
            case WAIT_LENS -> waitLens();
            case CAPTURE_COMMENT_LENS -> captureCommentLens(runtime);
            case CLOSE_FOR_RESUME -> closeForResume(runtime);
            case OPEN_RESUME_PALETTE -> openResumePalette(runtime);
            case SUBMIT_RESUME -> submitResume(runtime);
            case WAIT_RESUMED -> waitResumed(runtime);
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
        transition(Stage.CHOOSE_WRITABLE_VIEW);
        return false;
    }

    private boolean chooseWritableView(ISFMGamePuppetRuntime runtime) {
        if (!(Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette)) {
            return waitOrFail("the review-file context palette");
        }
        String command = palette.choiceCommandsForAutomation().stream()
                .filter(candidate -> candidate.startsWith(WRITABLE_VIEW_PREFIX))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "Review-file context menu omitted the writable view action: "
                                + palette.choiceCommandsForAutomation()));
        if (!palette.choiceReadyForPointerAutomation(command)) {
            return waitOrFail("the writable review choice to become pointer-ready");
        }
        runtime.clickActionChoice(command);
        transition(Stage.WAIT_REVIEW_EXPLORER);
        return false;
    }

    private boolean waitReviewExplorer() {
        Handle explorer = explorer(true);
        if (explorer == null) return waitOrFail("the generic release-review Explorer");
        reviewExplorerId = explorer.panel().explorerId().value();
        SFMReleaseReviewRuntime.Snapshot review = SFMReleaseReviewRuntime.get().snapshot();
        require(review.writable(), "mouse-opened release review did not acquire its writer lease");
        initialCommentCount = review.document().orElseThrow().reviewSession().comments().size();
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
            Optional<ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection.Row> textDiff = sourceRows.stream()
                    .filter(candidate -> candidate.entry().label().startsWith("text diff"))
                    .filter(candidate -> sameParent(before.path(), candidate.path()))
                    .findFirst();
            Optional<ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection.Row> structuredDiff = sourceRows.stream()
                    .filter(candidate -> candidate.entry().label().startsWith("structured diff"))
                    .filter(candidate -> sameParent(before.path(), candidate.path()))
                    .findFirst();
            if (after.isPresent() && textDiff.isPresent() && structuredDiff.isPresent()) {
                beforePath = before.path();
                afterPath = after.orElseThrow().path();
                textDiffPath = textDiff.orElseThrow().path();
                structuredDiffPath = structuredDiff.orElseThrow().path();
                transition(Stage.OPEN_BEFORE);
                return false;
            }
        }

        Optional<ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection.Row> collapsed =
                explorer.state().projection().rows().stream()
                        .filter(row -> row.entry().expandable())
                        .filter(row -> !explorer.state().session().expanded().contains(row.path()))
                        .findFirst();
        if (collapsed.isEmpty()) return waitOrFail("a before/after/text-diff/structured-diff quartet in Changes");
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
        transition(Stage.OPEN_TEXT_DIFF);
        return false;
    }

    private boolean waitDiff(SFMReleaseReviewSurfaceV1.SurfaceKind expectedKind) {
        SFMScreenMultiplexer workspace = workspace();
        if (workspace == null) return waitOrFail("the generated " + expectedKind.wireName() + " preview");
        Optional<SFMSourcePuppetProbe.EditorHandle> handle =
                SFMSourcePuppetProbe.editor(workspace, workspace.focusedPanelId());
        if (handle.isEmpty() || handle.orElseThrow().resolvedPanel().isEmpty()) {
            return waitOrFail("the resolved " + expectedKind.wireName() + " EditorV3 preview");
        }
        SFMTextDocumentSnapshot snapshot = handle.orElseThrow().state().documentSnapshot().orElse(null);
        if (snapshot == null || !snapshot.ready()) {
            if (snapshot != null) {
                throw new IllegalStateException("Generated " + expectedKind.wireName()
                        + " was unavailable: " + snapshot.diagnostics());
            }
            return waitOrFail("the ready " + expectedKind.wireName() + " document");
        }
        SFMReleaseReviewSurfaceV1.Surface surface = SFMReleaseReviewSurfaceRuntime.get()
                .sourceMap(snapshot).orElse(null);
        if (surface == null) return waitOrFail("the source map for " + expectedKind.wireName());
        require(surface.surfaceKind() == expectedKind,
                "Expected " + expectedKind + " but opened " + surface.surfaceKind());
        require(surface.complete(), "Generated " + expectedKind.wireName() + " was incomplete");
        require(!surface.mappings().isEmpty(), "Generated " + expectedKind.wireName() + " had no source mappings");
        if (expectedKind == SFMReleaseReviewSurfaceV1.SurfaceKind.TEXT_DIFF) {
            textDiffPanelId = handle.orElseThrow().panelId();
            transition(Stage.OPEN_STRUCTURED_DIFF);
        } else {
            structuredDiffPanelId = handle.orElseThrow().panelId();
            require(!structuredDiffPanelId.equals(textDiffPanelId),
                    "Text and structured diff reused one presentation entry");
            transition(Stage.REOPEN_TEXT_DIFF);
        }
        return false;
    }

    private boolean waitTextDiffDedup() {
        SFMScreenMultiplexer workspace = workspace();
        if (workspace == null) return waitOrFail("the deduplicated text-diff preview");
        require(previews(workspace).size() == 2,
                "text/structured/text browsing grew beyond two typed preview entries");
        if (!workspace.focusedPanelId().equals(textDiffPanelId)) {
            return waitOrFail("reopening text diff to focus its existing entry");
        }
        transition(Stage.CAPTURE_TEXT_DIFF);
        return false;
    }

    private boolean captureTextDiff(ISFMGamePuppetRuntime runtime) {
        if (!runtime.capture(
                "release-review-text-diff",
                caption("Lazy text diff with exact pinned before/after source mappings."))) return false;
        transition(Stage.SELECT_DIFF_TEXT);
        return false;
    }

    private boolean selectDiffText() {
        SFMScreenMultiplexer workspace = workspace();
        if (workspace == null) return waitOrFail("the text-diff editor before pointer selection");
        SFMSourcePuppetProbe.EditorHandle editor = SFMSourcePuppetProbe
                .editor(workspace, textDiffPanelId).orElse(null);
        if (editor == null || editor.resolvedPanel().isEmpty()) {
            return waitOrFail("the resolved text-diff editor before pointer selection");
        }
        SFMTextDocumentSnapshot snapshot = editor.state().documentSnapshot().orElseThrow();
        SFMReleaseReviewSurfaceV1.Surface surface = SFMReleaseReviewSurfaceRuntime.get()
                .sourceMap(snapshot).orElseThrow(() -> new IllegalStateException(
                        "The text-diff document lost its source map"));
        SFMTextDocumentRange selection = mappedCafeWord(surface);
        int startUtf16 = utf16Offset(snapshot.text(), selection.start().byteOffset());
        int endUtf16 = utf16Offset(snapshot.text(), selection.end().byteOffset());
        C11SourceNavigationPuppetProbe.Pointer start = C11SourceNavigationPuppetProbe.pointer(
                workspace,
                editor,
                SFMContextTextCoordinates.rangeAtUtf16Offsets(snapshot.text(), startUtf16, startUtf16 + 1)
        );
        selectionEnd = C11SourceNavigationPuppetProbe.pointer(
                workspace,
                editor,
                SFMContextTextCoordinates.rangeAtUtf16Offsets(snapshot.text(), endUtf16 - 1, endUtf16)
        );
        int contextUtf16 = Math.min(endUtf16 - 1, startUtf16 + 1);
        selectionContext = C11SourceNavigationPuppetProbe.pointer(
                workspace,
                editor,
                SFMContextTextCoordinates.rangeAtUtf16Offsets(
                        snapshot.text(), contextUtf16, contextUtf16 + 1)
        );
        SFMGamePuppetPointer.moveNative(workspace, start.globalX(), start.globalY());
        require(workspace.mouseClicked(start.globalX(), start.globalY(), GLFW.GLFW_MOUSE_BUTTON_LEFT),
                "Text-diff selection press was not handled");
        workspace.mouseMoved(selectionEnd.globalX(), selectionEnd.globalY());
        require(workspace.mouseDragged(
                        selectionEnd.globalX(), selectionEnd.globalY(), GLFW.GLFW_MOUSE_BUTTON_LEFT,
                        selectionEnd.globalX() - start.globalX(), selectionEnd.globalY() - start.globalY()),
                "Text-diff selection drag was not handled");
        require(workspace.mouseReleased(
                        selectionEnd.globalX(), selectionEnd.globalY(), GLFW.GLFW_MOUSE_BUTTON_LEFT),
                "Text-diff selection release was not handled");
        List<ca.teamdman.sfm.client.text_editor.SFMTextDocumentSelection> exactSelections =
                C11SourceNavigationPuppetProbe.exactDocumentSelections(editor)
                        .orElseThrow(() -> new IllegalStateException(
                                "Pointer drag published no exact generated-surface selection"));
        require(exactSelections.size() == 1 && !exactSelections.get(0).collapsed(),
                "Pointer drag did not publish one non-empty selection: " + exactSelections);
        SFMTextDocumentRange selected = exactSelections.get(0).orderedRange();
        List<SFMReleaseReviewSurfaceV1.SourceRange> projected = surface.sourceRangesFor(
                new SFMReleaseReviewSurfaceV1.Utf8Range(
                        selected.start().byteOffset(), selected.end().byteOffset()));
        require(!projected.isEmpty(),
                "Pointer drag missed every generated-surface mapping: selected=" + selected
                        + " mappings=" + surface.mappings());
        transition(Stage.OPEN_COMMENT_MENU);
        return false;
    }

    private boolean openCommentMenu() {
        SFMScreenMultiplexer workspace = workspace();
        if (workspace == null) return waitOrFail("the selected text-diff editor");
        require(selectionContext != null, "The text-diff selection interior was not captured");
        SFMGamePuppetPointer.moveNative(workspace, selectionContext.globalX(), selectionContext.globalY());
        require(workspace.mouseClicked(
                        selectionContext.globalX(), selectionContext.globalY(), GLFW.GLFW_MOUSE_BUTTON_RIGHT),
                "The selected text-diff context click was not handled");
        transition(Stage.CHOOSE_COMMENT_ROUTE);
        return false;
    }

    private boolean chooseCommentRoute(ISFMGamePuppetRuntime runtime) {
        if (!(Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette)) {
            return waitOrFail("the contextual action palette for selected diff text");
        }
        String command = palette.choiceCommandsForAutomation().stream()
                .filter(candidate -> candidate.startsWith(COMMENT_CHOICE_PREFIX))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "Selected source-mapped diff text offered no Comment action: "
                                + palette.choiceCommandsForAutomation()));
        if (!palette.choiceReadyForPointerAutomation(command)) {
            return waitOrFail("the Comment action to become pointer-ready");
        }
        runtime.clickActionChoice(command);
        transition(Stage.CHOOSE_APPROVED);
        return false;
    }

    private boolean chooseApproved(ISFMGamePuppetRuntime runtime) {
        if (!(Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette)) {
            return waitOrFail("the nested review-comment choice palette");
        }
        String command = palette.choiceCommandsForAutomation().stream()
                .filter(candidate -> candidate.startsWith(COMMENT_APPROVE_PREFIX))
                .filter(candidate -> candidate.contains("#approved"))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "Writable comment palette omitted #approved: " + palette.choiceCommandsForAutomation()));
        if (!palette.choiceReadyForPointerAutomation(command)) {
            return waitOrFail("the #approved choice to become pointer-ready");
        }
        runtime.clickActionChoice(command);
        transition(Stage.WAIT_COMMENT_SAVED);
        return false;
    }

    private boolean waitCommentSaved() {
        SFMReleaseReviewV1 review = SFMReleaseReviewRuntime.get().document().orElse(null);
        if (review == null || review.reviewSession().comments().size() <= initialCommentCount) {
            return waitOrFail("the pointer-created review comment to save");
        }
        var comment = review.reviewSession().comments().get(review.reviewSession().comments().size() - 1);
        require(comment.text().startsWith("#approved"), "Pointer-created comment lost #approved");
        createdCommentId = comment.id();
        createdCommentText = comment.text();
        var binding = review.selectorBindings().stream()
                .filter(candidate -> candidate.commentId().equals(createdCommentId))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "Pointer-created comment has no durable selector binding"));
        require(!binding.capturedSelection().ranges().isEmpty(),
                "Pointer-created comment has no pinned source range");
        require(binding.capturedSelection().ranges().stream().allMatch(range ->
                        range.documentRevisionId().equals(ReleaseReviewJourneyPuppetAction.CAFE_REVISION)
                                || range.documentRevisionId().equals(ReleaseReviewJourneyPuppetAction.AFTER_CAFE_REVISION)),
                "Text-diff source mapping escaped the Café before/after pair");
        require(SFMReleaseReviewKernel.query(review, "#approved intersect 1.19.2 HEAD")
                        .reviewUnitIds().contains(ReleaseReviewJourneyPuppetAction.CAFE_UNIT),
                "The newly persisted approval is absent from the pinned 1.19.2 HEAD query");
        SFMReleaseReviewV1 disk = readReviewFile();
        require(disk.reviewSession().comments().stream().anyMatch(value -> value.id().equals(createdCommentId)),
                "Atomic autosave omitted the pointer-created comment");
        transition(Stage.OPEN_LENS_MENU);
        return false;
    }

    private boolean openLensMenu() {
        Handle explorer = explorer(true);
        if (explorer == null) return waitOrFail("the review Explorer lens control");
        SFMExplorerPanelViewport.Rect control = explorer.panel().interactionLayout().lensControl();
        require(control.width() > 0 && control.height() > 0, "Review lens control is absent from layout");
        double[] point = workspacePoint(explorer, control);
        require(explorer.workspace().mouseClicked(point[0], point[1], GLFW.GLFW_MOUSE_BUTTON_LEFT),
                "Review lens control click was not handled");
        transition(Stage.CHOOSE_LENS);
        return false;
    }

    private boolean chooseLens(ISFMGamePuppetRuntime runtime) {
        if (!(Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette)) {
            return waitOrFail("the constrained review-lens palette");
        }
        if (lensMenuCommands.isEmpty()) {
            lensMenuCommands.addAll(palette.choiceCommandsForAutomation());
            for (SFMReleaseReviewExplorerScreenType.Projection projection
                    : SFMReleaseReviewExplorerScreenType.Projection.values()) {
                String expected = LENS_SET_PREFIX + lensToken(projection);
                require(lensMenuCommands.contains(expected),
                        "Review lens palette omitted " + expected + ": " + lensMenuCommands);
            }
        }
        SFMReleaseReviewExplorerScreenType.Projection projection = LENS_JOURNEY.get(nextLensIndex);
        String command = LENS_SET_PREFIX + lensToken(projection);
        if (!palette.choiceReadyForPointerAutomation(command)) {
            return waitOrFail("the " + projection + " lens choice to become pointer-ready");
        }
        runtime.clickActionChoice(command);
        transition(Stage.WAIT_LENS);
        return false;
    }

    private boolean waitLens() {
        Handle explorer = explorer(true);
        if (explorer == null) return waitOrFail("the review Explorer after switching lens");
        SFMReleaseReviewExplorerScreenType.Projection expected = LENS_JOURNEY.get(nextLensIndex);
        var descriptor = SFMReleaseReviewExplorerRuntime.get()
                .lensDescriptor(explorer.panel().sessionSnapshot().roots()).orElse(null);
        if (descriptor == null || descriptor.projection() != expected) {
            return waitOrFail("the " + expected + " review lens to publish");
        }
        List<String> labels = explorer.state().projection().rows().stream()
                .map(row -> row.entry().label()).toList();
        if (expected == SFMReleaseReviewExplorerScreenType.Projection.COMMENTS) {
            require(labels.stream().anyMatch(label -> label.contains(createdCommentId)
                            || label.contains(createdCommentText)),
                    "Comments lens did not refresh with the pointer-created comment: " + labels);
        }
        if (expected == SFMReleaseReviewExplorerScreenType.Projection.HASHTAGS) {
            require(labels.stream().anyMatch(label -> label.contains("#approved")),
                    "Hashtags lens did not refresh with #approved: " + labels);
        }
        visitedLenses.add(lensToken(expected));
        nextLensIndex++;
        if (expected == SFMReleaseReviewExplorerScreenType.Projection.COMMENTS) {
            transition(Stage.CAPTURE_COMMENT_LENS);
        } else if (nextLensIndex < LENS_JOURNEY.size()) transition(Stage.OPEN_LENS_MENU);
        else transition(Stage.CLOSE_FOR_RESUME);
        return false;
    }

    private boolean captureCommentLens(ISFMGamePuppetRuntime runtime) {
        if (!runtime.capture(
                "release-review-approved-comment",
                caption("A pointer-selected diff region persisted as #approved and refreshed Comments."))) {
            return false;
        }
        if (nextLensIndex < LENS_JOURNEY.size()) transition(Stage.OPEN_LENS_MENU);
        else transition(Stage.CLOSE_FOR_RESUME);
        return false;
    }

    private static Component caption(String text) {
        return Component.literal("SFM Release Review — ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.BLACK));
    }

    private boolean closeForResume(ISFMGamePuppetRuntime runtime) {
        require(readReviewFile().reviewSession().comments().stream()
                        .anyMatch(value -> value.id().equals(createdCommentId)),
                "Review comment disappeared before the resume boundary");
        SFMReleaseReviewRuntime.get().close();
        runtime.closeScreenNaturally();
        transition(Stage.OPEN_RESUME_PALETTE);
        return false;
    }

    private boolean openResumePalette(ISFMGamePuppetRuntime runtime) {
        if (Minecraft.getInstance().screen instanceof SFMScreenMultiplexer) {
            return waitOrFail("the release-review workspace to close naturally");
        }
        if (!runtime.openCommandPalette()) return waitOrFail("the resume command palette");
        transition(Stage.SUBMIT_RESUME);
        return false;
    }

    private boolean submitResume(ISFMGamePuppetRuntime runtime) {
        if (!(Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen)) {
            return waitOrFail("the resume command palette input");
        }
        runtime.executeCommandPalette(
                "sfm action invoke sfm:review/session/open/read_only/view " + reviewFile.toNativePath());
        transition(Stage.WAIT_RESUMED);
        return false;
    }

    private boolean waitResumed(ISFMGamePuppetRuntime runtime) {
        Handle explorer = explorer(true);
        if (explorer == null) return waitOrFail("the reopened release-review Changes Explorer");
        SFMReleaseReviewRuntime.Snapshot snapshot = SFMReleaseReviewRuntime.get().snapshot();
        require(!snapshot.writable(), "Resume proof unexpectedly reacquired a writer lease");
        require(snapshot.path().orElseThrow().equals(reviewFile.toNativePath().toAbsolutePath().normalize()),
                "Resume proof reopened a different review file");
        SFMReleaseReviewV1 review = snapshot.document().orElseThrow();
        require(review.reviewSession().comments().stream().anyMatch(value -> value.id().equals(createdCommentId)),
                "Reopened review omitted the pointer-created comment");
        List<String> approved = SFMReleaseReviewKernel.query(review, "#approved intersect 1.19.2 HEAD")
                .reviewUnitIds();
        require(approved.contains(ReleaseReviewJourneyPuppetAction.CAFE_UNIT),
                "Reopened review lost #approved intersect 1.19.2 HEAD membership");

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("schema", "sfm.release-review-mouse-comment-loop/1");
        evidence.put("review_file", reviewFile.canonical());
        evidence.put("text_diff_path", textDiffPath.canonical());
        evidence.put("structured_diff_path", structuredDiffPath.canonical());
        evidence.put("text_diff_panel", textDiffPanelId.toString());
        evidence.put("structured_diff_panel", structuredDiffPanelId.toString());
        evidence.put("created_comment_id", createdCommentId);
        evidence.put("created_comment_text", createdCommentText);
        evidence.put("lens_choices", List.copyOf(lensMenuCommands));
        evidence.put("visited_lenses", List.copyOf(visitedLenses));
        evidence.put("approved_intersect_1_19_2_head", approved);
        evidence.put("reopened_writable", snapshot.writable());
        runtime.writeArtifact(
                "release-review-mouse-comment-loop",
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

    private static SFMTextDocumentRange mappedCafeWord(SFMReleaseReviewSurfaceV1.Surface surface) {
        byte[] surfaceBytes = surface.text().getBytes(StandardCharsets.UTF_8);
        for (SFMReleaseReviewSurfaceV1.Mapping mapping : surface.mappings()) {
            for (SFMReleaseReviewSurfaceV1.SourceRange source : mapping.sourceRanges()) {
                if (!source.documentRevisionId().equals(ReleaseReviewJourneyPuppetAction.CAFE_REVISION)
                        && !source.documentRevisionId().equals(ReleaseReviewJourneyPuppetAction.AFTER_CAFE_REVISION)) {
                    continue;
                }
                int overlapStart = Math.max(CAFE_REVIEW_START, source.range().startByte());
                int overlapEnd = Math.min(CAFE_REVIEW_END, source.range().endByte());
                if (overlapStart >= overlapEnd) continue;
                int surfaceStart = mapping.surfaceRange().startByte()
                        + overlapStart - source.range().startByte();
                int surfaceEnd = surfaceStart + overlapEnd - overlapStart;
                String candidate = new String(
                        surfaceBytes,
                        surfaceStart,
                        surfaceEnd - surfaceStart,
                        StandardCharsets.UTF_8
                );
                int wordStart = -1;
                int wordEnd = -1;
                for (int index = 0; index < candidate.length();) {
                    int codePoint = candidate.codePointAt(index);
                    int next = index + Character.charCount(codePoint);
                    if (Character.isJavaIdentifierPart(codePoint)) {
                        if (wordStart < 0) wordStart = index;
                        wordEnd = next;
                    } else if (wordStart >= 0) {
                        break;
                    }
                    index = next;
                }
                if (wordStart < 0 || wordEnd <= wordStart) continue;
                int prefixUtf16 = new String(surfaceBytes, 0, surfaceStart, StandardCharsets.UTF_8).length();
                return SFMContextTextCoordinates.rangeAtUtf16Offsets(
                        surface.text(), prefixUtf16 + wordStart, prefixUtf16 + wordEnd);
            }
        }
        throw new IllegalStateException(
                "No generated text-diff word maps into the pinned Café review-unit range");
    }

    private static int utf16Offset(String text, int byteOffset) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (byteOffset < 0 || byteOffset > bytes.length) {
            throw new IllegalArgumentException("UTF-8 byte offset lies outside the generated document");
        }
        return new String(bytes, 0, byteOffset, StandardCharsets.UTF_8).length();
    }

    private SFMReleaseReviewV1 readReviewFile() {
        try {
            return SFMReleaseReviewV1Codec.parse(Files.readString(
                    reviewFile.toNativePath(), StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read staged release review " + reviewFile.canonical(), failure);
        }
    }

    private static String lensToken(SFMReleaseReviewExplorerScreenType.Projection projection) {
        return projection.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
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
        CHOOSE_WRITABLE_VIEW,
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
        WAIT_PANE_CLOSED,
        OPEN_TEXT_DIFF,
        WAIT_TEXT_DIFF,
        OPEN_STRUCTURED_DIFF,
        WAIT_STRUCTURED_DIFF,
        REOPEN_TEXT_DIFF,
        WAIT_TEXT_DIFF_DEDUP,
        CAPTURE_TEXT_DIFF,
        SELECT_DIFF_TEXT,
        OPEN_COMMENT_MENU,
        CHOOSE_COMMENT_ROUTE,
        CHOOSE_APPROVED,
        WAIT_COMMENT_SAVED,
        OPEN_LENS_MENU,
        CHOOSE_LENS,
        WAIT_LENS,
        CAPTURE_COMMENT_LENS,
        CLOSE_FOR_RESUME,
        OPEN_RESUME_PALETTE,
        SUBMIT_RESUME,
        WAIT_RESUMED
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
