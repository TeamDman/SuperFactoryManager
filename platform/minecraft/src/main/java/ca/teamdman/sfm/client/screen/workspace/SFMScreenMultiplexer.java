package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.context.SFMContextCaptureRequest;
import ca.teamdman.sfm.client.context.SFMContextCaptureService;
import ca.teamdman.sfm.client.context.SFMContextContributor;
import ca.teamdman.sfm.client.context.SFMContextOriginId;
import ca.teamdman.sfm.client.context.SFMContextSnapshot;
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingEngine;
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingService;
import ca.teamdman.sfm.client.keybinding.SFMKeyboardUsageContextSnapshot;
import ca.teamdman.sfm.client.keybinding.SFMKeyboardUsageSituationCatalog;
import ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituations;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionExecutor;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.SFMScissorStack;
import ca.teamdman.sfm.client.terminal.SFMTerminalPanel;
import ca.teamdman.sfm.client.terminal.SFMTerminalPropertiesPanel;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Owns Minecraft's Screen lifecycle while hosting a normalized tree of SFM panels. */
public final class SFMScreenMultiplexer extends Screen implements SFMWorkspacePanelHost {
    private static final int DIVIDER_WIDTH = 2;
    private static final int DIVIDER_HIT_SLOP = 3;
    private static final int MINIMUM_PANEL_PIXELS = 48;
    private static final int PANEL_BACKGROUND = 0xE0202020;
    private static final int FOCUSED_BORDER = 0xFF55FFFF;
    private static final int UNFOCUSED_BORDER = 0xFF606060;
    private static final ResourceLocation OPEN_PANEL = new ResourceLocation(SFM.MOD_ID, "panel/open");
    private static final ResourceLocation OPEN_PANEL_LEFT = new ResourceLocation(SFM.MOD_ID, "panel/open/left");
    private static final ResourceLocation OPEN_PANEL_RIGHT = new ResourceLocation(SFM.MOD_ID, "panel/open/right");
    private static final ResourceLocation OPEN_PANEL_ABOVE = new ResourceLocation(SFM.MOD_ID, "panel/open/above");
    private static final ResourceLocation OPEN_PANEL_BELOW = new ResourceLocation(SFM.MOD_ID, "panel/open/below");
    private static final ResourceLocation CLOSE_PANEL = new ResourceLocation(SFM.MOD_ID, "panel/close");
    private static final ResourceLocation CLOSE_SCREEN = new ResourceLocation(SFM.MOD_ID, "screen/close");
    private static final ResourceLocation CLOSE_PALETTE = new ResourceLocation(SFM.MOD_ID, "palette/close");

    private final @Nullable Screen previousScreen;
    private final SFMWorkspaceLayout layout;
    private final @Nullable SFMWorkspacePanelGroup panelGroup;
    private final Set<SFMWorkspacePanelId> openedPanels = new HashSet<>();
    private final Map<SFMWorkspacePanelId, SFMScreenPanel> openedPanelInstances = new java.util.HashMap<>();
    private final SFMPanelReopenCatalog reopenRecipes = new SFMPanelReopenCatalog();
    private Map<SFMWorkspacePanelId, SFMScreenPanelBounds> panelBounds = Map.of();
    private boolean closing;
    private @Nullable Component dropFeedback;
    private @Nullable WorkspaceToast workspaceToast;
    private long panelGroupRevision = Long.MIN_VALUE;
    private @Nullable SFMWorkspacePanelId observedFocusedPanel;
    private long keyboardFocusRevision;
    private long contextWorkspaceRevision;
    private long contextCaptureGeneration;
    private @Nullable SFMWorkspaceDividerInteraction dividerInteraction;

    private SFMScreenMultiplexer(
            @Nullable Screen previousScreen,
            SFMScreenPanel left,
            SFMScreenPanel right
    ) {
        this(previousScreen, SFMWorkspaceLayout.sideBySide(left, right), null);
    }

    private SFMScreenMultiplexer(
            @Nullable Screen previousScreen,
            SFMWorkspaceLayout layout
    ) {
        this(previousScreen, layout, null);
    }

    private SFMScreenMultiplexer(
            @Nullable Screen previousScreen,
            SFMWorkspaceLayout layout,
            @Nullable SFMWorkspacePanelGroup panelGroup
    ) {
        super(Component.literal("SFM workspace"));
        this.previousScreen = previousScreen;
        this.layout = layout;
        this.panelGroup = panelGroup;
    }

    public static SFMScreenMultiplexer create(
            @Nullable Screen previousScreen,
            SFMScreenPanel initialPanel
    ) {
        return new SFMScreenMultiplexer(previousScreen, SFMWorkspaceLayout.single(initialPanel));
    }

    public static SFMScreenMultiplexer create(
            @Nullable Screen previousScreen,
            SFMScreenPanel initialPanel,
            SFMPanelReopenRecipe reopenRecipe
    ) {
        SFMScreenMultiplexer workspace = create(previousScreen, initialPanel);
        workspace.registerReopenRecipe(initialPanel, Objects.requireNonNull(reopenRecipe));
        return workspace;
    }

    /** Opens an already-composed panel tree without flattening it into one application-specific panel. */
    public static SFMScreenMultiplexer create(
            @Nullable Screen previousScreen,
            SFMWorkspaceLayout layout
    ) {
        return new SFMScreenMultiplexer(previousScreen, layout);
    }

    public static SFMScreenMultiplexer create(
            @Nullable Screen previousScreen,
            SFMWorkspacePanelGroup group
    ) {
        SFMScreenPanelBounds initialBounds = new SFMScreenPanelBounds(0, 0, 1, 1);
        return new SFMScreenMultiplexer(previousScreen, SFMWorkspaceLayout.group(group.layout(initialBounds)), group);
    }

    public static void openToSide(@Nullable Screen origin, SFMScreenPanel panel) {
        openToSide(origin, SFMWorkspaceSide.RIGHT, panel);
    }

    public static void openToSide(
            @Nullable Screen origin,
            SFMWorkspaceSide side,
            SFMScreenPanel panel
    ) {
        openToSide(origin, side, panel, null);
    }

    public static void openToSide(
            @Nullable Screen origin,
            SFMWorkspaceSide side,
            SFMScreenPanel panel,
            @Nullable SFMPanelReopenRecipe reopenRecipe
    ) {
        if (origin instanceof SFMScreenMultiplexer multiplexer) {
            multiplexer.openToSide(multiplexer.layout.focusedPanel(), side, panel, reopenRecipe);
            return;
        }
        SFMScreenPanel previous = new SFMPreviousScreenPanel(origin);
        SFMScreenPanel first = side == SFMWorkspaceSide.LEFT || side == SFMWorkspaceSide.ABOVE
                ? panel : previous;
        SFMScreenPanel second = side == SFMWorkspaceSide.LEFT || side == SFMWorkspaceSide.ABOVE
                ? previous : panel;
        SFMWorkspaceLayout layout = side.axis() == SFMWorkspaceAxis.HORIZONTAL
                ? SFMWorkspaceLayout.group(SFMWorkspaceLayout.horizontal(
                SFMWorkspaceLayout.panel(first), SFMWorkspaceLayout.panel(second)))
                : SFMWorkspaceLayout.group(SFMWorkspaceLayout.vertical(
                SFMWorkspaceLayout.panel(first), SFMWorkspaceLayout.panel(second)));
        SFMWorkspacePanelId insertedId = layout.panels().stream()
                .filter(entry -> entry.panel() == panel)
                .findFirst()
                .orElseThrow()
                .id();
        layout.focus(insertedId);
        SFMScreenMultiplexer workspace = SFMScreenMultiplexer.create(origin, layout);
        workspace.registerReopenRecipe(panel, reopenRecipe);
        SFMScreenChangeHelpers.setScreen(workspace);
    }

    public static void openFocused(
            @Nullable Screen origin,
            SFMScreenPanel panel,
            SFMWorkspacePanelMetadata metadata
    ) {
        openFocused(origin, panel, metadata, null);
    }

    public static void openFocused(
            @Nullable Screen origin,
            SFMScreenPanel panel,
            SFMWorkspacePanelMetadata metadata,
            @Nullable SFMPanelReopenRecipe reopenRecipe
    ) {
        if (origin instanceof SFMScreenMultiplexer multiplexer) {
            multiplexer.openFocused(panel, metadata, reopenRecipe);
            return;
        }
        SFMScreenMultiplexer workspace = SFMScreenMultiplexer.create(origin, panel);
        workspace.registerReopenRecipe(panel, reopenRecipe);
        SFMScreenChangeHelpers.setScreen(workspace);
    }

    public void openToSide(SFMScreenPanel panel) {
        submit(layout.focusedPanel(), new SFMWorkspacePanelIntent.OpenToSide(SFMWorkspaceSide.RIGHT, panel));
    }

    public SFMWorkspacePanelIntentResult openToSide(
            SFMWorkspacePanelId source,
            SFMWorkspaceSide side,
            SFMScreenPanel panel
    ) {
        return openToSide(source, side, panel, null);
    }

    public SFMWorkspacePanelIntentResult openToSide(
            SFMWorkspacePanelId source,
            SFMWorkspaceSide side,
            SFMScreenPanel panel,
            @Nullable SFMPanelReopenRecipe reopenRecipe
    ) {
        return openToSide(
                source,
                side,
                panel,
                SFMWorkspacePanelMetadata.ordinary(),
                reopenRecipe
        );
    }

    public SFMWorkspacePanelIntentResult openToSide(
            SFMWorkspacePanelId source,
            SFMWorkspaceSide side,
            SFMScreenPanel panel,
            SFMWorkspacePanelMetadata metadata,
            @Nullable SFMPanelReopenRecipe reopenRecipe
    ) {
        if (layout.panel(source) == null) return SFMWorkspacePanelIntentResult.UNAVAILABLE;
        return submit(source, new SFMWorkspacePanelIntent.OpenToSide(
                side,
                panel,
                Objects.requireNonNull(metadata, "metadata"),
                reopenRecipe));
    }

    public SFMWorkspacePanelIntentResult openFocused(
            SFMScreenPanel panel,
            SFMWorkspacePanelMetadata metadata
    ) {
        return openFocused(panel, metadata, null);
    }

    public SFMWorkspacePanelIntentResult openFocused(
            SFMScreenPanel panel,
            SFMWorkspacePanelMetadata metadata,
            @Nullable SFMPanelReopenRecipe reopenRecipe
    ) {
        return submit(layout.focusedPanel(), new SFMWorkspacePanelIntent.OpenAsTab(
                panel,
                metadata,
                reopenRecipe));
    }

    public SFMWorkspacePanelIntentResult openIntoSlot(
            SFMWorkspacePanelId slot,
            SFMScreenPanel panel,
            SFMWorkspacePanelMetadata metadata
    ) {
        return openIntoSlot(slot, panel, metadata, null);
    }

    public SFMWorkspacePanelIntentResult openIntoSlot(
            SFMWorkspacePanelId slot,
            SFMScreenPanel panel,
            SFMWorkspacePanelMetadata metadata,
            @Nullable SFMPanelReopenRecipe reopenRecipe
    ) {
        if (layout.panel(slot) == null) return SFMWorkspacePanelIntentResult.UNAVAILABLE;
        return submit(slot, new SFMWorkspacePanelIntent.OpenAsTab(panel, metadata, reopenRecipe));
    }

    public boolean focusPanel(SFMWorkspacePanelId panelId) {
        boolean focused = layout.focus(panelId);
        if (focused) refreshLayout(true);
        return focused;
    }

    /** Focuses a visible panel using the one-based index exposed to users. */
    public boolean focusVisiblePanel(int oneBasedIndex) {
        List<SFMWorkspaceLayout.PanelEntry> panels = layout.visiblePanels();
        int index = oneBasedIndex - 1;
        if (index < 0 || index >= panels.size()) return false;
        return focusPanel(panels.get(index).id());
    }

    /** Applies the same traversal used by the Ctrl+Tab workspace shortcut. */
    public boolean traversePanelFocus(int direction) {
        boolean changed = layout.traverse(direction);
        if (changed) {
            refreshLayout(true);
            synchronizeWidgetHostActivation();
        }
        return changed;
    }

    public boolean canToggleMaximize() {
        return panelGroup != null && focusedPanelInstance() != null;
    }

    /** Applies the same maximize behavior used by the Ctrl+M workspace shortcut. */
    public boolean toggleMaximizeFocusedPanel() {
        SFMScreenPanel focused = focusedPanelInstance();
        if (panelGroup == null || focused == null) return false;
        panelGroup.toggleMaximize(focused);
        refreshLayout(true);
        return true;
    }

    public boolean containsPanel(SFMWorkspacePanelId panelId) {
        return layout.panel(panelId) != null;
    }

    public SFMWorkspacePanelIntentResult closeFocused() {
        return submit(layout.focusedPanel(), new SFMWorkspacePanelIntent.Close());
    }

    public SFMWorkspacePanelIntentResult closePanel(SFMWorkspacePanelId panelId) {
        if (layout.panel(panelId) == null) return SFMWorkspacePanelIntentResult.UNAVAILABLE;
        return submit(panelId, new SFMWorkspacePanelIntent.Close());
    }

    public SFMWorkspacePanelIntentResult moveFocused(SFMWorkspaceSide side) {
        return submit(layout.focusedPanel(), new SFMWorkspacePanelIntent.Move(side));
    }

    public boolean canDuplicate(SFMWorkspacePanelId panelId) {
        return duplicateUnavailableReason(panelId).isEmpty();
    }

    public Optional<Component> duplicateUnavailableReason(SFMWorkspacePanelId panelId) {
        if (panelGroup != null) {
            return Optional.of(Component.literal(
                    "Responsive panel groups do not yet retain dynamic duplicate entries"));
        }
        SFMScreenPanel panel = layout.panel(panelId);
        if (panel == null) return Optional.of(Component.literal("The source panel is no longer available"));
        Optional<SFMPanelReopenRecipe> recipe = reopenRecipes.recipeFor(panel);
        if (recipe.isEmpty()) {
            return Optional.of(Component.literal(
                    "The captured panel does not expose a typed re-open recipe"));
        }
        return recipe.orElseThrow().unavailableReason(new SFMPanelReopenContext(this, panelId));
    }

    public SFMWorkspacePanelIntentResult duplicatePanel(
            SFMWorkspacePanelId panelId,
            SFMWorkspaceSide side
    ) {
        SFMScreenPanel source = layout.panel(panelId);
        if (source == null) return SFMWorkspacePanelIntentResult.UNAVAILABLE;
        if (duplicateUnavailableReason(panelId).isPresent()) {
            return SFMWorkspacePanelIntentResult.UNSUPPORTED;
        }
        Optional<SFMPanelReopenCatalog.ReopenedPanel> reopened = reopenRecipes.reopen(source);
        if (reopened.isEmpty()) return SFMWorkspacePanelIntentResult.UNSUPPORTED;
        SFMScreenPanel duplicate = reopened.orElseThrow().panel();
        SFMPanelReopenRecipe recipe = reopened.orElseThrow().recipe();
        SFMWorkspacePanelMetadata metadata = layout.metadata(panelId);
        return submit(panelId, new SFMWorkspacePanelIntent.OpenToSide(
                side,
                duplicate,
                metadata == null ? SFMWorkspacePanelMetadata.ordinary() : metadata,
                recipe));
    }

    public boolean canResizePanel(SFMWorkspacePanelId panelId, SFMWorkspaceSide side) {
        if (this.width <= 0 || this.height <= 0) return false;
        return layout.canResize(
                panelId,
                side,
                new SFMScreenPanelBounds(0, 0, this.width, this.height),
                DIVIDER_WIDTH,
                MINIMUM_PANEL_PIXELS);
    }

    public SFMWorkspacePanelIntentResult resizePanel(
            SFMWorkspacePanelId panelId,
            SFMWorkspaceSide side
    ) {
        if (this.width <= 0 || this.height <= 0 || !layout.resize(
                panelId,
                side,
                new SFMScreenPanelBounds(0, 0, this.width, this.height),
                DIVIDER_WIDTH,
                MINIMUM_PANEL_PIXELS)) {
            return SFMWorkspacePanelIntentResult.UNAVAILABLE;
        }
        refreshLayout(true, false);
        return SFMWorkspacePanelIntentResult.APPLIED;
    }

    public Optional<SFMPanelReopenRecipe> reopenRecipe(SFMWorkspacePanelId panelId) {
        SFMScreenPanel panel = layout.panel(panelId);
        return panel == null ? Optional.empty() : reopenRecipes.recipeFor(panel);
    }

    /** Replaces reconstruction metadata after a panel changes its typed source address. */
    public boolean setPanelReopenRecipe(
            SFMWorkspacePanelId panelId,
            @Nullable SFMPanelReopenRecipe reopenRecipe
    ) {
        SFMScreenPanel panel = layout.panel(panelId);
        if (panel == null) return false;
        if (reopenRecipe == null) reopenRecipes.remove(panel);
        else reopenRecipes.register(panel, reopenRecipe);
        return true;
    }

    public boolean setFocusedGuiScale(@Nullable Integer scale) {
        boolean changed = layout.setFocusedGuiScale(scale);
        if (changed) refreshLayout(true);
        return changed;
    }

    public boolean setPanelGuiScale(SFMWorkspacePanelId panelId, @Nullable Integer scale) {
        boolean changed = layout.setGuiScale(panelId, scale);
        if (changed) refreshLayout(true);
        return changed;
    }

    public List<SFMWorkspaceLayout.PanelEntry> panelSlotEntries(SFMWorkspacePanelId panelId) {
        return layout.slotEntries(panelId);
    }

    public boolean rotateVisibleContent(int direction) {
        boolean changed = layout.rotateVisibleContent(direction);
        if (changed) refreshLayout(true);
        return changed;
    }

    public boolean rotateVisibleScale(int direction) {
        boolean changed = layout.rotateVisibleScale(direction);
        if (changed) refreshLayout(true);
        return changed;
    }

    public List<SFMWorkspaceLayout.PanelEntry> visiblePanelEntries() {
        return layout.visiblePanels();
    }

    public List<SFMWorkspaceLayout.PanelEntry> focusedSlotEntries() {
        return layout.focusedSlotEntries();
    }

    /** Traversal index retained for automation; panel identity is exposed separately. */
    public int focusedPanel() {
        List<SFMWorkspaceLayout.PanelEntry> panels = layout.panels();
        for (int i = 0; i < panels.size(); i++) {
            if (panels.get(i).id().equals(layout.focusedPanel())) return i;
        }
        return -1;
    }

    public SFMWorkspacePanelId focusedPanelId() {
        return layout.focusedPanel();
    }

    /** Current keyboard-focus revision without forcing a full context capture. */
    public long keyboardFocusGeneration() {
        observeWorkspaceFocus();
        return keyboardFocusRevision;
    }

    /** Exact visible panel targeted by focused-panel actions. */
    public @Nullable SFMScreenPanel focusedPanelInstance() {
        return layout.panel(layout.focusedPanel());
    }

    public @Nullable SFMScreenPanel panelInstance(SFMWorkspacePanelId panelId) {
        return layout.panel(panelId);
    }

    /** Host-owned metadata for an exact panel entry, including preview ownership. */
    public @Nullable SFMWorkspacePanelMetadata panelMetadata(SFMWorkspacePanelId panelId) {
        return layout.metadata(panelId);
    }

    public List<SFMScreenPanel> panels() {
        return layout.panels().stream().map(SFMWorkspaceLayout.PanelEntry::panel).toList();
    }

    /** Captures every relevant visible panel independently; hidden tab entries are not guessed into context. */
    public SFMContextSnapshot contextSnapshot() {
        observeWorkspaceFocus();
        contextCaptureGeneration = incrementContextGeneration(contextCaptureGeneration);
        return captureVisibleContext(
                layout.visiblePanels(),
                layout.panel(layout.focusedPanel()),
                contextCaptureGeneration,
                contextWorkspaceRevision,
                keyboardFocusRevision
        );
    }

    /** Pure snapshot assembly seam shared by the live screen and headless contract tests. */
    static SFMContextSnapshot captureVisibleContext(
            List<SFMWorkspaceLayout.PanelEntry> visiblePanels,
            @Nullable SFMScreenPanel focusedPanel,
            long captureGeneration,
            long workspaceGeneration,
            long focusGeneration
    ) {
        List<SFMContextContributor> contributors = visiblePanels.stream()
                .map(SFMWorkspaceLayout.PanelEntry::panel)
                .filter(SFMContextContributor.class::isInstance)
                .map(SFMContextContributor.class::cast)
                .toList();
        Optional<SFMContextOriginId> focusedOrigin = Optional.empty();
        if (focusedPanel instanceof SFMContextContributor contributor) {
            focusedOrigin = contributor.focusedOriginId();
        }
        return new SFMContextCaptureService(contributors).capture(new SFMContextCaptureRequest(
                captureGeneration,
                workspaceGeneration,
                focusGeneration,
                focusedOrigin
        ));
    }

    public List<SFMWorkspacePanelId> panelIds() {
        return layout.panels().stream().map(SFMWorkspaceLayout.PanelEntry::id).toList();
    }

    public Optional<SFMWorkspaceStackId> panelStackId(SFMWorkspacePanelId panelId) {
        return layout.stackId(panelId);
    }

    public @Nullable SFMScreenPanelBounds panelBounds(SFMWorkspacePanelId panelId) {
        return panelBounds.get(panelId);
    }

    /** Returns the logical content allocation after applying the entry's render scale. */
    public @Nullable SFMScreenPanelBounds panelContentBounds(SFMWorkspacePanelId panelId) {
        SFMWorkspaceLayout.PanelEntry entry = layout.entry(panelId);
        return entry == null ? null : contentBounds(entry);
    }

    public List<SFMWorkspaceDivider> dividerDescriptions() {
        if (this.width <= 0 || this.height <= 0) return List.of();
        return layout.dividers(
                workspaceViewport(),
                DIVIDER_WIDTH,
                DIVIDER_HIT_SLOP,
                MINIMUM_PANEL_PIXELS);
    }

    /** Logical and physical rectangles are exposed together for automation evidence. */
    public List<SFMWorkspaceDividerView> dividerViews() {
        if (this.minecraft == null) return dividerDescriptions().stream()
                .map(divider -> new SFMWorkspaceDividerView(
                        divider, divider.lineBounds(), divider.hitBounds()))
                .toList();
        var window = this.minecraft.getWindow();
        SFMScreenPanelBounds viewport = workspaceViewport();
        return dividerDescriptions().stream()
                .map(divider -> SFMWorkspaceDividerView.scale(
                        divider, viewport, window.getWidth(), window.getHeight()))
                .toList();
    }

    public SFMWorkspaceDividerInteraction.Snapshot dividerInteractionSnapshot() {
        SFMWorkspaceDividerInteraction interaction = dividerInteraction;
        return interaction == null
                ? new SFMWorkspaceDividerInteraction.Snapshot(
                SFMWorkspaceDividerCursor.DEFAULT,
                List.of(),
                List.of(),
                null,
                null,
                0.0D,
                0.0D,
                Map.of(),
                Map.of(),
                Map.of())
                : interaction.snapshot();
    }

    public SFMWorkspaceDividerResizeResult resizeDividers(SFMWorkspaceResizeDividersIntent intent) {
        if (this.width <= 0 || this.height <= 0) {
            return new SFMWorkspaceDividerResizeResult(
                    SFMWorkspaceDividerResizeResult.Status.UNAVAILABLE,
                    Map.of(),
                    Map.of(),
                    Map.of());
        }
        SFMWorkspaceDividerResizeResult result = layout.resizeDividers(
                intent,
                workspaceViewport(),
                DIVIDER_WIDTH,
                DIVIDER_HIT_SLOP,
                MINIMUM_PANEL_PIXELS);
        if (result.changed()) {
            if (this.minecraft == null) {
                panelBounds = layout.bounds(workspaceViewport(), DIVIDER_WIDTH);
            } else {
                refreshLayout(true, false);
            }
        }
        return result;
    }

    @Override
    public SFMWorkspacePanelIntentResult submit(
            SFMWorkspacePanelId source,
            SFMWorkspacePanelIntent intent
    ) {
        @Nullable SFMPanelReopenRecipe insertedRecipe = null;
        if (intent instanceof SFMWorkspacePanelIntent.OpenToSide open) {
            insertedRecipe = open.reopenRecipe();
        } else if (intent instanceof SFMWorkspacePanelIntent.OpenAsTab open) {
            insertedRecipe = open.reopenRecipe();
        }
        SFMWorkspacePanelIntentDispatcher.Outcome outcome =
                SFMWorkspacePanelIntentDispatcher.apply(layout, source, intent);
        if (outcome.result() != SFMWorkspacePanelIntentResult.APPLIED) return outcome.result();
        if (outcome.moved()) {
            refreshLayout(true);
            return SFMWorkspacePanelIntentResult.APPLIED;
        }
        if (outcome.inserted() != null) {
            registerReopenRecipe(layout.panel(outcome.inserted()), insertedRecipe);
            refreshLayout(true);
            return SFMWorkspacePanelIntentResult.APPLIED;
        }
        outcome.removedPanel().widgetHost().ifPresent(SFMPanelWidgetHost::closed);
        outcome.removedPanel().closed();
        openedPanels.remove(outcome.removed());
        openedPanelInstances.remove(outcome.removed());
        reopenRecipes.remove(outcome.removedPanel());
        if (layout.panels().isEmpty()) {
            onClose();
        } else {
            refreshLayout(true);
        }
        return SFMWorkspacePanelIntentResult.APPLIED;
    }

    @Override
    public Optional<SFMWorkspacePanelMetrics> measure(
            SFMWorkspacePanelId source,
            SFMScreenPanelBounds logicalBounds
    ) {
        SFMWorkspaceLayout.PanelEntry entry = layout.entry(source);
        SFMScreenPanelBounds slotBounds = panelBounds.get(source);
        if (entry == null || slotBounds == null || this.minecraft == null) return Optional.empty();
        SFMScreenPanelBounds guiContent = slotBounds.inset(1);
        double panelScale = panelRenderScale(entry);
        var window = this.minecraft.getWindow();
        int guiWidth = Math.max(1, window.getGuiScaledWidth());
        int guiHeight = Math.max(1, window.getGuiScaledHeight());
        return Optional.of(SFMWorkspacePanelMetrics.map(
                logicalBounds,
                guiContent.x(),
                guiContent.y(),
                panelScale,
                entry.metadata().guiScaleOverride() == null ? 0 : entry.metadata().guiScaleOverride(),
                window.getWidth(),
                window.getHeight(),
                guiWidth,
                guiHeight
        ));
    }

    @Override
    public Optional<SFMScreenPanel> panel(SFMWorkspacePanelId panelId) {
        return Optional.ofNullable(layout.panel(panelId));
    }

    @Override
    protected void init() {
        disposeDividerInteraction();
        super.init();
        refreshLayout(true);
        if (this.minecraft != null) {
            dividerInteraction = new SFMWorkspaceDividerInteraction(
                    new SFMWorkspaceDividerInteraction.Host() {
                        @Override
                        public SFMWorkspaceLayout layout() {
                            return layout;
                        }

                        @Override
                        public SFMScreenPanelBounds viewport() {
                            return workspaceViewport();
                        }

                        @Override
                        public int dividerPixels() {
                            return DIVIDER_WIDTH;
                        }

                        @Override
                        public int hitSlopPixels() {
                            return DIVIDER_HIT_SLOP;
                        }

                        @Override
                        public int minimumPanelPixels() {
                            return MINIMUM_PANEL_PIXELS;
                        }

                        @Override
                        public void dividerLayoutChanged() {
                            refreshLayout(true, false);
                        }
                    },
                    SFMWorkspaceGlfwCursorHost.live(this.minecraft.getWindow().getWindow()));
        }
    }

    private void refreshLayout(boolean notifyPanels) {
        refreshLayout(notifyPanels, true);
    }

    private void refreshLayout(boolean notifyPanels, boolean recomposePanelGroup) {
        contextWorkspaceRevision = incrementContextGeneration(contextWorkspaceRevision);
        if (panelGroup != null && recomposePanelGroup) {
            layout.recompose(panelGroup.layout(new SFMScreenPanelBounds(0, 0, this.width, this.height)));
            panelGroupRevision = panelGroup.revision();
        }
        if (dividerInteraction != null) dividerInteraction.synchronizeLayoutRevision();
        panelBounds = layout.bounds(new SFMScreenPanelBounds(0, 0, this.width, this.height), DIVIDER_WIDTH);
        synchronizeWidgetHostActivation();
        observeWorkspaceFocus();
        if (!notifyPanels || this.minecraft == null) return;
        // A hidden stack entry remains a live panel. Closing it here destroys
        // panel-local state (notably its PTY and selected transport) merely
        // because another entry became visible in the same slot. Panels close
        // only when removed from the layout or when the workspace itself closes.
        for (SFMWorkspaceLayout.PanelEntry entry : layout.visiblePanels()) {
            if (openedPanels.contains(entry.id())) {
                SFMScreenPanel opened = openedPanelInstances.get(entry.id());
                if (opened != entry.panel()) {
                    if (opened != null) {
                        opened.widgetHost().ifPresent(SFMPanelWidgetHost::closed);
                        opened.closed();
                    }
                    openPanel(entry.id(), entry.panel());
                } else {
                    entry.panel().resized(this.minecraft, contentBounds(entry));
                }
            } else {
                openPanel(entry.id(), entry.panel());
            }
        }
    }

    private SFMScreenPanelBounds workspaceViewport() {
        return new SFMScreenPanelBounds(0, 0, Math.max(0, this.width), Math.max(0, this.height));
    }

    private void disposeDividerInteraction() {
        SFMWorkspaceDividerInteraction interaction = dividerInteraction;
        dividerInteraction = null;
        if (interaction != null) interaction.close();
    }

    private static long incrementContextGeneration(long value) {
        return value == Long.MAX_VALUE ? value : value + 1;
    }

    private void openPanel(SFMWorkspacePanelId id, SFMScreenPanel panel) {
        SFMWorkspaceLayout.PanelEntry entry = layout.panels().stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst().orElseThrow();
        panel.opened(this.minecraft, contentBounds(entry), new SFMWorkspacePanelContext(id, this));
        openedPanels.add(id);
        openedPanelInstances.put(id, panel);
    }

    private void registerReopenRecipe(
            @Nullable SFMScreenPanel panel,
            @Nullable SFMPanelReopenRecipe reopenRecipe
    ) {
        if (panel != null && reopenRecipe != null) reopenRecipes.register(panel, reopenRecipe);
    }

    @Override
    public void tick() {
        if (panelGroup != null && panelGroupRevision != panelGroup.revision()) refreshLayout(true);
        if (dividerInteraction != null && this.minecraft != null
                && GLFW.glfwGetWindowAttrib(
                this.minecraft.getWindow().getWindow(), GLFW.GLFW_FOCUSED) != GLFW.GLFW_TRUE) {
            dividerInteraction.focusLost();
        }
        for (SFMWorkspaceLayout.PanelEntry entry : layout.visiblePanels()) entry.panel().tick();
        ca.teamdman.sfm.client.symbol.SFMFindReferencesController.tickProduction(this);
        if (workspaceToast != null && workspaceToast.isExpired(System.nanoTime())) workspaceToast = null;
    }

    /** Shows the current panel scale without permanently occupying panel space. */
    public void showGuiScaleToast(@Nullable Integer override, int inheritedScale, boolean shake) {
        String label = override == null
                ? "gui scale auto (" + inheritedScale + ")"
                : "gui scale " + override;
        showWorkspaceToast(Component.literal(label), shake);
    }

    /** Shared transient status surface for asynchronous panel actions. */
    public void showWorkspaceToast(Component message, boolean shake) {
        workspaceToast = new WorkspaceToast(Objects.requireNonNull(message, "message"), System.nanoTime(), shake);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public Component getNarrationMessage() {
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
        Component narration = focused == null
                ? Component.literal("Empty SFM workspace")
                : Component.literal("SFM workspace. Focused panel: ").append(focused.narration());
        return dropFeedback == null ? narration : narration.copy().append(Component.literal(". ")).append(dropFeedback);
    }

    @Override
    @MCVersionDependentBehaviour
    protected void updateNarrationState(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getNarrationMessage());
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
        if (focused != null) {
            focused.widgetHost().ifPresent(host -> host.updateFocusedNarration(output.nest()));
        }
    }

    @Override
    public void onClose() {
        if (closing) return;
        closing = true;
        disposeDividerInteraction();
        for (SFMWorkspaceLayout.PanelEntry entry : layout.allPanels()) {
            entry.panel().widgetHost().ifPresent(SFMPanelWidgetHost::closed);
            entry.panel().closed();
        }
        openedPanels.clear();
        openedPanelInstances.clear();
        reopenRecipes.clear();
        SFMScreenChangeHelpers.setScreen(previousScreen);
    }

    @Override
    public void removed() {
        disposeDividerInteraction();
        ca.teamdman.sfm.client.symbol.SFMFindReferencesController.workspaceRemovedProduction(this);
        super.removed();
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        synchronizeWidgetHostActivation();
        this.renderBackground(poseStack);
        for (SFMWorkspaceLayout.PanelEntry entry : layout.visiblePanels()) {
            SFMScreenPanelBounds bounds = panelBounds.get(entry.id());
            if (bounds == null) continue;
            fill(poseStack, bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), PANEL_BACKGROUND);
            int border = entry.id().equals(layout.focusedPanel()) ? FOCUSED_BORDER : UNFOCUSED_BORDER;
            fill(poseStack, bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + 1, border);
            fill(poseStack, bounds.x(), bounds.y() + bounds.height() - 1, bounds.x() + bounds.width(), bounds.y() + bounds.height(), border);
            fill(poseStack, bounds.x(), bounds.y(), bounds.x() + 1, bounds.y() + bounds.height(), border);
            fill(poseStack, bounds.x() + bounds.width() - 1, bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), border);
            SFMScreenPanelBounds contentBounds = bounds.inset(1);
            SFMScissorStack.pushGui(
                    contentBounds.x(),
                    contentBounds.y(),
                    contentBounds.x() + contentBounds.width(),
                    contentBounds.y() + contentBounds.height()
            );
            try {
                renderPanelEntry(poseStack, entry, contentBounds, mouseX, mouseY, partialTick);
            } finally {
                SFMScissorStack.pop();
            }
            renderEntryAffordances(poseStack, entry, bounds);
        }
        renderDividerAffordances(poseStack);
        if (dropFeedback != null) {
            SFMFontUtils.draw(poseStack, this.font, dropFeedback, 6, Math.max(2, this.height - 12), 0xFFFF7777, true);
        }
        super.render(poseStack, mouseX, mouseY, partialTick);
        renderWorkspaceToast(poseStack);
        if (dividerInteraction != null) dividerInteraction.reassertCursor();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE
                && dividerInteraction != null
                && dividerInteraction.isCaptured()) {
            dividerInteraction.cancel();
            return true;
        }
        boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 || Screen.hasControlDown();
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 || Screen.hasShiftDown();
        if (panelGroup != null && control && keyCode == GLFW.GLFW_KEY_M) {
            return invokeWorkspaceAction("sfm:panel/maximize/toggle");
        }
        if (control && keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            int requestedIndex = keyCode - GLFW.GLFW_KEY_1;
            return invokeWorkspaceAction(
                    "sfm:panel/focus/index " + (requestedIndex + 1));
        }
        if (control && keyCode == GLFW.GLFW_KEY_TAB) {
            return invokeWorkspaceAction(
                    shift ? "sfm:panel/focus/previous" : "sfm:panel/focus/next");
        }
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
        if (focused != null && focused.widgetHost()
                .map(host -> host.keyPressed(keyCode, scanCode, modifiers)).orElse(false)) return true;
        if (focused != null && !focused.widgetHostOwnsInput()
                && focused.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            SFMCommandPaletteScreen.openChoices(Component.literal("Close SFM workspace"), escapeChoices());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean invokeWorkspaceAction(String actionDraft) {
        String command = "sfm action invoke " + actionDraft;
        try {
            SFMClientActionContext context = SFMClientActionContext.create(
                    this, () -> Minecraft.getInstance() != null
                            && Minecraft.getInstance().screen == this);
            return SFMClientActionExecutor.execute(command, context, ignored -> { }) > 0;
        } catch (CommandSyntaxException exception) {
            SFM.LOGGER.warn("Workspace semantic action failed: {}", command, exception);
            return false;
        }
    }

    static List<SFMActionChoice> diagnosticChoices() {
        return diagnosticChoices(null);
    }

    public static List<SFMActionChoice> diagnosticChoices(@Nullable SFMScreenPanel focused) {
        String scene = "sfm:size_display";
        List<SFMActionChoice> choices = new ArrayList<>();
        if (focused instanceof SFMTerminalPanel terminal && terminal.isRustBacked()) {
            choices.add(SFMActionChoice.invoke(OPEN_PANEL_RIGHT, "sfm:terminal_properties"));
        } else if (focused instanceof SFMTerminalPropertiesPanel) {
            choices.add(SFMActionChoice.invoke(CLOSE_PANEL, ""));
        }
        choices.add(SFMActionChoice.invoke(OPEN_PANEL, scene));
        choices.add(SFMActionChoice.invoke(OPEN_PANEL_LEFT, scene));
        choices.add(SFMActionChoice.invoke(OPEN_PANEL_RIGHT, scene));
        choices.add(SFMActionChoice.invoke(OPEN_PANEL_ABOVE, scene));
        choices.add(SFMActionChoice.invoke(OPEN_PANEL_BELOW, scene));
        return List.copyOf(choices);
    }

    static List<SFMActionChoice> escapeChoices() {
        return List.of(
                SFMActionChoice.invoke(CLOSE_PANEL, ""),
                SFMActionChoice.invoke(CLOSE_SCREEN, ""),
                SFMActionChoice.invoke(CLOSE_PALETTE, "")
        );
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
        return focused != null && (focused.widgetHost()
                .map(host -> host.keyReleased(keyCode, scanCode, modifiers)).orElse(false)
                || !focused.widgetHostOwnsInput() && focused.keyReleased(keyCode, scanCode, modifiers)
                || super.keyReleased(keyCode, scanCode, modifiers));
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
        return focused != null && (focused.widgetHost()
                .map(host -> host.charTyped(character, modifiers)).orElse(false)
                || !focused.widgetHostOwnsInput() && focused.charTyped(character, modifiers)
                || super.charTyped(character, modifiers));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (dividerInteraction != null
                && dividerInteraction.pointerPressed(mouseX, mouseY, button)) return true;
        SFMWorkspaceLayout.PanelEntry entry = panelAt(mouseX, mouseY);
        if (entry != null) {
            layout.focus(entry.id());
            synchronizeWidgetHostActivation();
            int[] local = localMouse(entry, mouseX, mouseY);
            boolean childHandled = entry.panel().widgetHost()
                    .map(host -> host.mouseClicked(local[0], local[1], button)).orElse(false);
            if (!childHandled && !entry.panel().widgetHostOwnsInput()) {
                entry.panel().mouseClicked(local[0], local[1], button);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (dividerInteraction != null) {
            dividerInteraction.pointerMoved(mouseX, mouseY);
            if (dividerInteraction.isHoveringDivider() || dividerInteraction.isCaptured()) {
                super.mouseMoved(mouseX, mouseY);
                return;
            }
        }
        SFMWorkspaceLayout.PanelEntry entry = panelAt(mouseX, mouseY);
        if (entry != null) {
            int[] local = localMouse(entry, mouseX, mouseY);
            entry.panel().widgetHost().ifPresent(host -> host.mouseMoved(local[0], local[1]));
            if (!entry.panel().widgetHostOwnsInput()) entry.panel().mouseMoved(local[0], local[1]);
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dividerInteraction != null
                && dividerInteraction.pointerReleased(mouseX, mouseY, button)) return true;
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
        SFMWorkspaceLayout.PanelEntry entry = focused == null ? null : layout.entry(layout.focusedPanel());
        int[] local = entry == null ? new int[]{0, 0} : localMouse(entry, mouseX, mouseY);
        return focused != null && (focused.widgetHost()
                .map(host -> host.mouseReleased(local[0], local[1], button)).orElse(false)
                || !focused.widgetHostOwnsInput() && focused.mouseReleased(local[0], local[1], button)
                || super.mouseReleased(mouseX, mouseY, button));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dividerInteraction != null
                && dividerInteraction.pointerDragged(mouseX, mouseY, button)) return true;
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
        SFMWorkspaceLayout.PanelEntry entry = focused == null ? null : layout.entry(layout.focusedPanel());
        int[] local = entry == null ? new int[]{0, 0} : localMouse(entry, mouseX, mouseY);
        double scale = entry == null ? 1.0D : panelRenderScale(entry);
        double localDragX = dragX / scale;
        double localDragY = dragY / scale;
        return focused != null && (focused.widgetHost()
                .map(host -> host.mouseDragged(local[0], local[1], button, localDragX, localDragY)).orElse(false)
                || !focused.widgetHostOwnsInput()
                && focused.mouseDragged(local[0], local[1], button, localDragX, localDragY)
                || super.mouseDragged(mouseX, mouseY, button, dragX, dragY));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        SFMWorkspaceLayout.PanelEntry entry = panelAt(mouseX, mouseY);
        if (entry != null) {
            layout.focus(entry.id());
            synchronizeWidgetHostActivation();
        }
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
        SFMWorkspaceLayout.PanelEntry focusedEntry = focused == null ? null : layout.entry(layout.focusedPanel());
        int[] local = focusedEntry == null ? new int[]{0, 0} : localMouse(focusedEntry, mouseX, mouseY);
        return focused != null && (focused.widgetHost()
                .map(host -> host.mouseScrolled(local[0], local[1], delta)).orElse(false)
                || !focused.widgetHostOwnsInput() && focused.mouseScrolled(local[0], local[1], delta)
                || super.mouseScrolled(mouseX, mouseY, delta));
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
        if (focused instanceof SFMFileDropTarget dropTarget) {
            dropFeedback = null;
            dropTarget.onFilesDrop(List.copyOf(paths));
        } else {
            dropFeedback = Component.literal("Drop rejected: focused panel does not accept directories");
        }
    }

    private @Nullable SFMWorkspaceLayout.PanelEntry panelAt(double mouseX, double mouseY) {
        for (SFMWorkspaceLayout.PanelEntry entry : layout.visiblePanels()) {
            SFMScreenPanelBounds bounds = panelBounds.get(entry.id());
            if (bounds != null && bounds.contains(mouseX, mouseY)) return entry;
        }
        return null;
    }

    private SFMScreenPanelBounds contentBounds(SFMWorkspaceLayout.PanelEntry entry) {
        SFMScreenPanelBounds bounds = panelBounds.get(entry.id());
        if (bounds == null) return new SFMScreenPanelBounds(0, 0, 0, 0);
        SFMScreenPanelBounds physical = bounds.inset(1);
        double scale = panelRenderScale(entry);
        return new SFMScreenPanelBounds(
                0,
                0,
                Math.max(1, (int) Math.ceil(physical.width() / scale)),
                Math.max(1, (int) Math.ceil(physical.height() / scale))
        );
    }

    private void renderPanelEntry(
            PoseStack poseStack,
            SFMWorkspaceLayout.PanelEntry entry,
            SFMScreenPanelBounds physicalBounds,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        double scale = panelRenderScale(entry);
        SFMScreenPanelBounds logicalBounds = contentBounds(entry);
        int[] local = localMouse(entry, mouseX, mouseY);
        poseStack.pushPose();
        poseStack.translate(physicalBounds.x(), physicalBounds.y(), 0.0D);
        poseStack.scale((float) scale, (float) scale, 1.0F);
        entry.panel().render(
                poseStack,
                this.minecraft,
                logicalBounds,
                local[0],
                local[1],
                partialTick,
                entry.id().equals(layout.focusedPanel())
        );
        entry.panel().widgetHost().ifPresent(host -> host.render(poseStack, local[0], local[1], partialTick));
        poseStack.popPose();
    }

    private double panelRenderScale(SFMWorkspaceLayout.PanelEntry entry) {
        Integer override = entry.metadata().guiScaleOverride();
        if (override == null || this.minecraft == null) return 1.0D;
        return override / Math.max(1.0D, this.minecraft.getWindow().getGuiScale());
    }

    private int[] localMouse(SFMWorkspaceLayout.PanelEntry entry, double mouseX, double mouseY) {
        SFMScreenPanelBounds physical = panelBounds.get(entry.id());
        if (physical == null) return new int[]{0, 0};
        SFMScreenPanelBounds content = physical.inset(1);
        double scale = panelRenderScale(entry);
        return SFMPanelWidgetHost.panelCoordinates(content, scale, mouseX, mouseY);
    }

    private void synchronizeWidgetHostActivation() {
        SFMWorkspacePanelId focusedPanel = layout.focusedPanel();
        for (SFMWorkspaceLayout.PanelEntry entry : layout.allPanels()) {
            entry.panel().widgetHost().ifPresent(host -> host.setActive(entry.id().equals(focusedPanel)));
        }
    }

    /** Captures the exact panel/widget context targeted by one key event. */
    public SFMKeyboardUsageContextSnapshot keyboardUsageContextSnapshot() {
        observeWorkspaceFocus();
        SFMWorkspacePanelId panelId = layout.focusedPanel();
        SFMScreenPanel panel = layout.panel(panelId);
        @Nullable SFMPanelWidgetHost host = panel == null
                ? null
                : panel.widgetHost().orElse(null);
        @Nullable SFMPanelWidget child = host == null
                ? null
                : host.focusedChild().orElse(null);
        ResourceLocation deepest = child == null
                ? (panel == null ? SFMKeyboardUsageSituations.DEFAULT : panel.keyboardUsageSituationId())
                : child.keyboardUsageSituationId();
        SFMKeyboardUsageSituationCatalog.ActiveAncestry ancestry =
                SFMKeyboardUsageSituations.catalog().resolve(deepest);
        List<ResourceLocation> situations = ancestry.situations();
        @Nullable ResourceLocation elementId = child == null ? null : child.elementId();
        long capturedWorkspaceRevision = keyboardFocusRevision;
        long capturedElementRevision = host == null ? 0 : host.focusRevision();
        return new SFMKeyboardUsageContextSnapshot(
                this,
                () -> isKeyboardUsageContextCurrent(
                        panelId,
                        elementId,
                        capturedWorkspaceRevision,
                        capturedElementRevision),
                panelId,
                elementId,
                capturedWorkspaceRevision,
                capturedElementRevision,
                situations,
                ancestry.depths()
        );
    }

    private boolean isKeyboardUsageContextCurrent(
            SFMWorkspacePanelId panelId,
            @Nullable ResourceLocation elementId,
            long workspaceRevision,
            long elementRevision
    ) {
        if (Minecraft.getInstance().screen != this
                || keyboardFocusRevision != workspaceRevision
                || !layout.focusedPanel().equals(panelId)) return false;
        SFMScreenPanel panel = layout.panel(panelId);
        if (panel == null) return false;
        @Nullable SFMPanelWidgetHost host = panel.widgetHost().orElse(null);
        if (host == null) return elementId == null && elementRevision == 0;
        return host.focusRevision() == elementRevision
                && java.util.Objects.equals(host.focusedElementId().orElse(null), elementId);
    }

    private void observeWorkspaceFocus() {
        SFMWorkspacePanelId focused = layout.focusedPanel();
        if (java.util.Objects.equals(observedFocusedPanel, focused)) return;
        observedFocusedPanel = focused;
        keyboardFocusRevision++;
        // Pure layout/action tests construct a workspace without bootstrapping
        // Minecraft. Revision identity still updates there; only the live
        // input engine needs an eager reset.
        if (Minecraft.getInstance() != null) {
            SFMKeyBindingService.INSTANCE.reset(SFMKeyBindingEngine.ResetReason.CONTEXT_CHANGED);
        }
    }


    private void renderEntryAffordances(
            PoseStack poseStack,
            SFMWorkspaceLayout.PanelEntry entry,
            SFMScreenPanelBounds bounds
    ) {
        List<SFMWorkspaceLayout.PanelEntry> slot = layout.slotEntries(entry.id());
        if (slot.size() > 1) {
            int boxSize = 11;
            int gap = 2;
            int totalWidth = slot.size() * boxSize + (slot.size() - 1) * gap;
            int startX = Math.max(bounds.x() + 2, bounds.x() + bounds.width() - totalWidth - 3);
            int y = Math.max(bounds.y() + 2, bounds.y() + bounds.height() - boxSize - 3);
            for (int index = 0; index < slot.size(); index++) {
                SFMWorkspaceLayout.PanelEntry tab = slot.get(index);
                int x = startX + index * (boxSize + gap);
                int background = tab.id().equals(layout.focusedPanel()) ? 0xFF55FFFF : 0xCC303030;
                int foreground = tab.id().equals(layout.focusedPanel()) ? 0xFF101010 : 0xFFFFFFFF;
                fill(poseStack, x, y, x + boxSize, y + boxSize, background);
                String label = Integer.toString(index + 1);
                SFMFontUtils.draw(poseStack, this.font, label,
                        x + (boxSize - this.font.width(label)) / 2,
                        y + 2,
                        foreground,
                        true);
            }
        }
    }

    private void renderDividerAffordances(PoseStack poseStack) {
        if (dividerInteraction == null) return;
        SFMWorkspaceDividerInteraction.Snapshot snapshot = dividerInteraction.snapshot();
        Set<SFMWorkspaceDividerId> hovered = new HashSet<>(snapshot.hoveredDividerIds());
        Set<SFMWorkspaceDividerId> captured = new HashSet<>(snapshot.capturedDividerIds());
        for (SFMWorkspaceDivider divider : dividerDescriptions()) {
            if (!hovered.contains(divider.id()) && !captured.contains(divider.id())) continue;
            SFMScreenPanelBounds line = divider.lineBounds();
            int color = captured.contains(divider.id()) ? 0xFFFFAA33 : 0xFF55FFFF;
            fill(poseStack, line.x(), line.y(), line.x() + line.width(), line.y() + line.height(), color);
        }
    }

    private void renderWorkspaceToast(PoseStack poseStack) {
        if (workspaceToast == null) return;
        long elapsedNanos = Math.max(0L, System.nanoTime() - workspaceToast.startedNanos());
        double elapsedMillis = elapsedNanos / 1_000_000.0D;
        if (elapsedMillis >= WorkspaceToast.DURATION_MILLIS) return;

        float opacity = workspaceToast.opacity(elapsedMillis);
        int alpha = Math.max(0, Math.min(255, Math.round(opacity * 255.0F)));
        String text = workspaceToast.message().getString();
        int paddingX = 8;
        int paddingY = 5;
        int boxWidth = this.font.width(text) + paddingX * 2;
        int boxHeight = this.font.lineHeight + paddingY * 2;
        int shakeOffset = workspaceToast.shakeOffset(elapsedMillis);
        int left = Math.max(2, (this.width - boxWidth) / 2 + shakeOffset);
        int top = Math.max(2, this.height - boxHeight - 18);
        int right = Math.min(this.width - 2, left + boxWidth);
        int bottom = Math.min(this.height - 2, top + boxHeight);
        fill(poseStack, left, top, right, bottom, withAlpha(0x20252B, alpha));
        fill(poseStack, left, top, right, top + 1, withAlpha(0x55FFFF, alpha));
        fill(poseStack, left, bottom - 1, right, bottom, withAlpha(0x55FFFF, alpha));
        fill(poseStack, left, top, left + 1, bottom, withAlpha(0x55FFFF, alpha));
        fill(poseStack, right - 1, top, right, bottom, withAlpha(0x55FFFF, alpha));
        SFMFontUtils.draw(poseStack, this.font, text, left + paddingX, top + paddingY,
                withAlpha(0xFFFFFF, alpha), true);
    }

    private static int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    private record WorkspaceToast(Component message, long startedNanos, boolean shake) {
        private static final double DURATION_MILLIS = 2_200.0D;
        private static final double FADE_IN_MILLIS = 140.0D;
        private static final double FADE_OUT_MILLIS = 650.0D;
        private static final double SHAKE_MILLIS = 480.0D;

        private boolean isExpired(long nowNanos) {
            return nowNanos - startedNanos >= (long) (DURATION_MILLIS * 1_000_000.0D);
        }

        private float opacity(double elapsedMillis) {
            if (elapsedMillis < FADE_IN_MILLIS) {
                return (float) (elapsedMillis / FADE_IN_MILLIS);
            }
            double fadeOutStart = DURATION_MILLIS - FADE_OUT_MILLIS;
            if (elapsedMillis > fadeOutStart) {
                return (float) Math.max(0.0D, (DURATION_MILLIS - elapsedMillis) / FADE_OUT_MILLIS);
            }
            return 1.0F;
        }

        private int shakeOffset(double elapsedMillis) {
            if (!shake || elapsedMillis >= SHAKE_MILLIS) return 0;
            double strength = 1.0D - elapsedMillis / SHAKE_MILLIS;
            return (int) Math.round(Math.sin(elapsedMillis / 24.0D * Math.PI * 2.0D) * 3.0D * strength);
        }
    }

}
