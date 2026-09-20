package ca.teamdman.sfm.client.screen.review.explorer;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMReleaseReviewAction;
import ca.teamdman.sfm.client.context.SFMContextTextCoordinates;
import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewAnalysisIdentityResolver;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewKernel;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewRuntime;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditorPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMCandidateHistoryScreenType;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelReopenRecipe;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntent;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntentResult;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelMetadata;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceSide;
import ca.teamdman.sfm.client.screen.workspace.timeline.SFMTimelinePanel;
import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSource;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorPanelRecipe;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorPanelOpenContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Explorer-first review surface whose source leaves open through Text Editor v3. */
public final class SFMReviewExplorerPanel implements SFMScreenPanel {
    private static final int ROW_HEIGHT = 18;
    private static final int HEADER_HEIGHT = 37;
    private static final int BACKGROUND = 0xF0202020;
    private static final int HEADER = 0xF02A2A2A;
    private static final int SELECTED = 0xFF264F78;
    private static final int TEXT = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int ERROR = 0xFFFF7777;

    private SFMReviewExplorerModel model;
    private final String title;
    private final @Nullable Supplier<Object> liveRevision;
    private final @Nullable Supplier<SFMReviewExplorerModel> liveProjection;
    private @Nullable Object observedRevision;
    private @Nullable SFMWorkspacePanelContext hostContext;
    private @Nullable SFMWorkspacePanelId previewSlot;
    private int firstVisibleRow;
    private int listTop;
    private int listBottom;
    private String status = "Space preview · Ctrl+Enter open a new stacked panel";

    public SFMReviewExplorerPanel(String title, SFMReviewExplorerModel model) {
        this(title, model, null, null);
    }

    public SFMReviewExplorerPanel(
            String title,
            SFMReviewExplorerModel model,
            @Nullable Supplier<Object> liveRevision,
            @Nullable Supplier<SFMReviewExplorerModel> liveProjection
    ) {
        this.title = title;
        this.model = model;
        if ((liveRevision == null) != (liveProjection == null)) {
            throw new IllegalArgumentException("Live review projection requires both revision and model suppliers");
        }
        this.liveRevision = liveRevision;
        this.liveProjection = liveProjection;
        this.observedRevision = liveRevision == null ? null : liveRevision.get();
    }

    public SFMReviewExplorerModel model() { return model; }
    public String status() { return status; }

    /** Stable selected-row witness used by viewport-independent puppet automation. */
    public String selectedNodeIdForAutomation() { return model.selected().id(); }

    @Override public Component title() { return Component.literal(title); }

    @Override
    public Component narration() {
        return Component.literal(title + ". Selected: " + model.selected().label() + ". " + status);
    }

    @Override
    public void opened(Minecraft minecraft, SFMScreenPanelBounds bounds, SFMWorkspacePanelContext context) {
        hostContext = context;
        resize(bounds);
    }

    @Override
    public void resized(Minecraft minecraft, SFMScreenPanelBounds bounds) {
        resize(bounds);
    }

    @Override
    public void closed() {
        hostContext = null;
        previewSlot = null;
    }

    @Override
    public void tick() {
        if (liveRevision == null || liveProjection == null) return;
        Object currentRevision = liveRevision.get();
        if (currentRevision == observedRevision) return;
        SFMReviewExplorerModel.ViewState viewState = model.captureViewState();
        model = liveProjection.get();
        model.restoreViewState(viewState);
        observedRevision = currentRevision;
        keepSelectionVisible();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> model.selectPrevious();
            case GLFW.GLFW_KEY_DOWN -> model.selectNext();
            case GLFW.GLFW_KEY_HOME -> model.selectFirst();
            case GLFW.GLFW_KEY_END -> model.selectLast();
            case GLFW.GLFW_KEY_RIGHT -> model.expandSelection();
            case GLFW.GLFW_KEY_LEFT -> model.collapseSelectionOrSelectParent();
            case GLFW.GLFW_KEY_SPACE -> openSelected(false);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> openSelected(
                    (modifiers & GLFW.GLFW_MOD_CONTROL) != 0);
            case GLFW.GLFW_KEY_MENU -> openMigrationChoices();
            case GLFW.GLFW_KEY_F10 -> {
                if ((modifiers & GLFW.GLFW_MOD_SHIFT) == 0) return false;
                openMigrationChoices();
            }
            default -> { return false; }
        }
        keepSelectionVisible();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if ((button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT)
                || mouseY < listTop || mouseY >= listBottom) return false;
        int index = firstVisibleRow + (int) ((mouseY - listTop) / ROW_HEIGHT);
        if (index < 0 || index >= model.visibleNodes().size()) return true;
        model.select(index);
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            openMigrationChoices();
            keepSelectionVisible();
            return true;
        }
        if (mouseX < 18 + model.visibleNodes().get(index).depth() * 14) {
            model.toggleSelection();
        } else if (model.selectedLeaf() != null || model.selected().action().isPresent()) {
            openSelected(false);
        }
        keepSelectionVisible();
        return true;
    }

    private void openMigrationChoices() {
        String prefix = "release/migration/";
        if (!model.selected().id().startsWith(prefix)) {
            status = "No contextual migration actions are available for this row";
            return;
        }
        if (hostContext == null) {
            status = "Migration actions unavailable: explorer is not hosted";
            return;
        }
        String migrationId = model.selected().id().substring(prefix.length());
        SFMReleaseReviewV1 review = SFMReleaseReviewRuntime.get().document().orElse(null);
        SFMReleaseReviewV1.MigrationReport report = Optional.ofNullable(review)
                .flatMap(value -> value.migrationReports().stream()
                        .filter(candidate -> candidate.id().equals(migrationId)).findFirst())
                .orElse(null);
        if (report == null) {
            status = "Migration report is no longer available: " + migrationId;
            return;
        }
        if (report.decision() != SFMReleaseReviewV1.MigrationDecision.UNRESOLVED
                && report.decision() != SFMReleaseReviewV1.MigrationDecision.DEFERRED) {
            status = "Migration already resolved as " + report.decision().name().toLowerCase(java.util.Locale.ROOT);
            return;
        }
        ResourceLocation actionId = new ResourceLocation(
                SFM.MOD_ID, SFMReleaseReviewAction.Kind.MIGRATION_DECIDE.path());
        String migrationArgument = StringArgumentType.escapeIfRequired(migrationId);
        String expectedState = SFMReleaseReviewKernel.semanticStateHash(Objects.requireNonNull(review));
        String arguments = migrationArgument + " " + expectedState + " ";
        ArrayList<SFMActionChoice> choices = new ArrayList<>();
        if (report.candidateEvaluation().status() == SFMReleaseReviewV1.EvaluationStatus.RELOCATED) {
            choices.add(SFMActionChoice.invoke(actionId,
                    arguments + "relocation-confirmed none Explicitly confirmed the witnessed relocation.",
                    "Confirm witnessed relocation"));
        }
        for (int index = 0; index < report.newCandidates().size(); index++) {
            int displayed = index + 1;
            choices.add(SFMActionChoice.invoke(actionId,
                    arguments + "retargeted " + displayed + " Explicitly retargeted to candidate " + displayed + ".",
                    "Retarget to candidate " + displayed));
            choices.add(SFMActionChoice.invoke(actionId,
                    arguments + "selector-edited " + displayed
                            + " Explicitly edited the selector to candidate " + displayed + ".",
                    "Edit selector to candidate " + displayed));
        }
        choices.addAll(List.of(
                SFMActionChoice.invoke(actionId,
                        arguments + "archived none Explicitly archived the source comment.",
                        "Archive source comment"),
                SFMActionChoice.invoke(actionId,
                        arguments + "discarded none Explicitly discarded the source comment.",
                        "Discard source comment"),
                SFMActionChoice.invoke(actionId,
                        arguments + "deferred none Deferred for later human review.",
                        "Defer migration")
        ));
        SFMCommandPaletteScreen.openChoices(
                new SFMClientActionContext(hostContext.host(), () -> true, hostContext.panelId()),
                Component.literal("Resolve migration · " + migrationId),
                choices
        );
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxFirst = Math.max(0, model.visibleNodes().size() - visibleRowCount());
        firstVisibleRow = Math.max(0, Math.min(maxFirst, firstVisibleRow + (delta > 0 ? -1 : 1)));
        return true;
    }

    @Override
    public void render(PoseStack poseStack, Minecraft minecraft, SFMScreenPanelBounds bounds,
                       int mouseX, int mouseY, float partialTick, boolean focused) {
        GuiComponent.fill(poseStack, bounds.x(), bounds.y(), bounds.x() + bounds.width(),
                bounds.y() + bounds.height(), BACKGROUND);
        GuiComponent.fill(poseStack, bounds.x(), bounds.y(), bounds.x() + bounds.width(),
                bounds.y() + HEADER_HEIGHT, HEADER);
        int x = bounds.x() + 8;
        SFMFontUtils.draw(poseStack, minecraft.font, title, x, bounds.y() + 6, TEXT, true);
        SFMFontUtils.draw(poseStack, minecraft.font,
                minecraft.font.plainSubstrByWidth(status, Math.max(0, bounds.width() - 16)),
                x, bounds.y() + 20, MUTED, false);

        listTop = bounds.y() + HEADER_HEIGHT;
        listBottom = bounds.y() + bounds.height() - 16;
        var visible = model.visibleNodes();
        int end = Math.min(visible.size(), firstVisibleRow + visibleRowCount());
        for (int index = firstVisibleRow; index < end; index++) {
            SFMReviewExplorerModel.VisibleNode row = visible.get(index);
            int y = listTop + (index - firstVisibleRow) * ROW_HEIGHT;
            if (index == model.selectionIndex()) {
                GuiComponent.fill(poseStack, bounds.x() + 1, y, bounds.x() + bounds.width() - 1,
                        y + ROW_HEIGHT, SELECTED);
            }
            int indent = bounds.x() + 7 + row.depth() * 14;
            String disclosure = row.node().expandable() ? (row.node().expanded() ? "v" : ">") : "·";
            int colour = row.node().leaf() != null && row.node().leaf().missing() ? ERROR : TEXT;
            SFMFontUtils.draw(poseStack, minecraft.font, disclosure, indent, y + 5, colour, true);
            String label = minecraft.font.plainSubstrByWidth(row.node().label(),
                    Math.max(0, bounds.x() + bounds.width() - indent - 14));
            SFMFontUtils.draw(poseStack, minecraft.font, label, indent + 10, y + 5, colour, false);
        }
        SFMFontUtils.draw(poseStack, minecraft.font,
                minecraft.font.plainSubstrByWidth("Selected: " + model.selected().label(),
                        Math.max(0, bounds.width() - 16)),
                x, bounds.y() + bounds.height() - 12, MUTED, false);
    }

    private void openSelected(boolean newStack) {
        SFMReviewExplorerModel.NodeAction action = model.selected().action().orElse(null);
        if (action instanceof SFMReviewExplorerModel.CandidateNavigation candidateNavigation) {
            openCandidateTarget(candidateNavigation);
            return;
        }
        SFMReviewExplorerModel.SourceLeaf leaf = model.selectedLeaf();
        if (leaf == null) {
            status = "Selected node is a group; expand it and choose a source leaf";
            return;
        }
        if (leaf.missing()) {
            status = "Cannot open tombstone: " + leaf.path();
            return;
        }
        if (hostContext == null) {
            status = "Preview unavailable: explorer is not hosted";
            return;
        }
        SFMPanelReopenRecipe reopenRecipe = new SFMTextEditorPanelRecipe(
                new ResourceLocation(SFM.MOD_ID, "text_editor"),
                SFMTextEditors.V3.getId().orElseThrow().location(),
                documentSource(leaf),
                true,
                "Review · " + leaf.title()
        );
        SFMScreenPanel preview = reopenRecipe.reopen();
        SFMWorkspacePanelMetadata metadata = SFMWorkspacePanelMetadata.explorerPreview(hostContext.panelId().toString());
        SFMWorkspacePanelIntentResult result;
        SFMScreenMultiplexer workspace = hostContext.host() instanceof SFMScreenMultiplexer value ? value : null;
        if (newStack) {
            result = hostContext.submit(new SFMWorkspacePanelIntent.OpenAsTab(
                    preview,
                    metadata,
                    reopenRecipe));
            if (result == SFMWorkspacePanelIntentResult.APPLIED) status = "Opened new panel: " + leaf.title();
            else status = "Open unavailable: " + result;
            return;
        }
        if (workspace != null && previewSlot != null && workspace.containsPanel(previewSlot)) {
            result = workspace.openIntoSlot(previewSlot, preview, metadata, reopenRecipe);
        } else {
            result = hostContext.submit(new SFMWorkspacePanelIntent.OpenToSide(
                    SFMWorkspaceSide.RIGHT,
                    preview,
                    metadata,
                    reopenRecipe));
            if (result == SFMWorkspacePanelIntentResult.APPLIED && workspace != null) {
                previewSlot = workspace.focusedPanelId();
            }
        }
        if (result == SFMWorkspacePanelIntentResult.APPLIED) {
            status = "Preview opened: " + leaf.title();
            if (workspace != null) workspace.focusPanel(hostContext.panelId());
        } else {
            status = "Preview unavailable: " + result;
        }
    }

    private static SFMTextDocumentSource documentSource(SFMReviewExplorerModel.SourceLeaf leaf) {
        if (leaf.documentRevisionId().isEmpty()) return new SFMTextDocumentSource.Literal(leaf.text());
        String revisionId = leaf.documentRevisionId().orElseThrow();
        ArrayList<String> rootSegments = new ArrayList<>();
        rootSegments.add(revisionId);
        SFMPath root = new SFMPath(
                SFMPath.Kind.CONTRIBUTED,
                "review",
                "document",
                rootSegments,
                Optional.empty(),
                true
        );
        ArrayList<String> pathSegments = new ArrayList<>(rootSegments);
        for (String segment : leaf.path().replace('\\', '/').split("/")) {
            if (!segment.isEmpty()) pathSegments.add(segment);
        }
        SFMPath path = new SFMPath(
                SFMPath.Kind.CONTRIBUTED,
                "review",
                "document",
                pathSegments,
                Optional.empty(),
                false
        );
        Optional<SFMTextDocumentRange> targetRange = leaf.targetRange().map(range -> {
            List<Integer> offsets = SFMContextTextCoordinates.utf16OffsetsAtUtf8Bytes(
                    leaf.text(), List.of(range.startByte(), range.endByte()));
            return SFMContextTextCoordinates.rangeAtUtf16Offsets(leaf.text(), offsets.get(0), offsets.get(1));
        });
        return new SFMTextDocumentSource.PinnedSnapshot(
                path,
                root,
                leaf.text(),
                leaf.sha256().orElseThrow(),
                targetRange,
                Optional.empty(),
                SFMReleaseReviewRuntime.get().document().flatMap(review ->
                        SFMReleaseReviewRuntime.get().path().flatMap(reviewPath ->
                                SFMReleaseReviewAnalysisIdentityResolver.resolve(
                                        review, reviewPath, revisionId)))
        );
    }

    private void openCandidateTarget(SFMReviewExplorerModel.CandidateNavigation navigation) {
        if (hostContext == null) {
            status = "Candidate navigation unavailable: explorer is not hosted";
            return;
        }
        var target = navigation.target();
        ResourceLocation scene = new ResourceLocation(SFM.MOD_ID, "episode/candidate-history");
        String exactMachine = SFMEntitySelector.exact(
                SFMEntitySelector.Domain.EPISODE,
                target.machineId()
        ).canonical();
        SFMCandidateHistoryScreenType.Recipe recipe = new SFMCandidateHistoryScreenType.Recipe(
                scene,
                exactMachine,
                java.util.Optional.of(target.trajectoryPlanRevisionId()),
                java.util.Optional.of(target.routeId())
        );
        SFMTimelinePanel panel = recipe.reopen();
        panel.seek(target.routeStepPosition());
        SFMWorkspacePanelIntentResult result = hostContext.submit(new SFMWorkspacePanelIntent.OpenAsTab(
                panel,
                SFMWorkspacePanelMetadata.ordinary(),
                recipe
        ));
        status = result == SFMWorkspacePanelIntentResult.APPLIED
                ? "Opened candidate " + target.canonicalAddress() + " for " + navigation.commentId()
                : "Candidate navigation unavailable: " + result;
    }

    private void resize(SFMScreenPanelBounds bounds) {
        listTop = bounds.y() + HEADER_HEIGHT;
        listBottom = bounds.y() + bounds.height() - 16;
        keepSelectionVisible();
    }

    private int visibleRowCount() {
        return Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
    }

    private void keepSelectionVisible() {
        int selected = model.selectionIndex();
        int visible = visibleRowCount();
        if (selected < firstVisibleRow) firstVisibleRow = selected;
        if (selected >= firstVisibleRow + visible) firstVisibleRow = selected - visible + 1;
        firstVisibleRow = Math.max(0, firstVisibleRow);
    }
}
