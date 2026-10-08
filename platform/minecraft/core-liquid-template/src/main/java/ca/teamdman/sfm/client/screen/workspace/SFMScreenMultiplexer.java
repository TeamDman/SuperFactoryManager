package ca.teamdman.sfm.client.screen.workspace;

{% if features.workspace_panel_actions or features.workspace_toast_actions or features.workspace_notifications %}
import ca.teamdman.sfm.SFM;
{% endif %}
{% if features.context_actions %}
import ca.teamdman.sfm.client.context.SFMContextCaptureRequest;
{% endif %}
{% if features.context_actions %}
import ca.teamdman.sfm.client.context.SFMContextCaptureService;
{% endif %}
{% if features.context_actions %}
import ca.teamdman.sfm.client.context.SFMContextContributor;
{% endif %}
{% if features.context_actions %}
import ca.teamdman.sfm.client.context.SFMContextOriginId;
{% endif %}
{% if features.context_actions %}
import ca.teamdman.sfm.client.context.SFMContextSnapshot;
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.client_overlay_scenes %}
import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
{% endif %}
{% endif %}
{% if features.workspace_keyboard_context %}
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingEngine;
{% endif %}
{% if features.workspace_keyboard_context %}
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingService;
{% endif %}
{% if features.workspace_keyboard_context %}
import ca.teamdman.sfm.client.keybinding.SFMKeyboardUsageContextSnapshot;
{% endif %}
{% if features.workspace_keyboard_context %}
import ca.teamdman.sfm.client.keybinding.SFMKeyboardUsageContextProvider;
{% endif %}
{% if features.workspace_keyboard_context %}
import ca.teamdman.sfm.client.keybinding.SFMKeyboardUsageSituationCatalog;
{% endif %}
{% if features.workspace_keyboard_context %}
import ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituations;
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.client_overlay_scenes %}
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract;
{% endif %}
{% endif %}
{% if features.workspace_panel_actions or features.workspace_toast_actions %}
import ca.teamdman.sfm.client.action.SFMClientActionContext;
{% endif %}
{% if features.workspace_panel_actions or features.workspace_toast_actions %}
import ca.teamdman.sfm.client.action.SFMClientActionExecutor;
{% endif %}
{% if features.workspace_notifications %}
{% if features.workspace_toast_path_actions %}
import ca.teamdman.sfm.client.action.SFMToastPathAction;
{% endif %}
{% endif %}
{% if features.workspace_notifications %}
{% if features.workspace_toast_path_actions %}
import ca.teamdman.sfm.client.explorer.SFMPath;
{% endif %}
{% endif %}
{% if features.workspace_panel_entry_controls %}
import ca.teamdman.sfm.client.action.SFMPanelEntryInteractionSessionService;
{% endif %}
{% if features.workspace_panel_entry_controls %}
import ca.teamdman.sfm.client.action.SFMWorkspaceLifecycleActionIds;
{% endif %}
{% if features.workspace_panel_actions or features.workspace_toast_actions %}
import ca.teamdman.sfm.client.screen.SFMActionChoice;
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.command_palette %}
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
{% else %}
{% if features.workspace_toast_actions %}
{% if features.command_palette %}
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
{% endif %}
{% endif %}
{% endif %}
{% else %}
{% if features.workspace_toast_actions %}
{% if features.command_palette %}
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
{% endif %}
{% endif %}
{% endif %}
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
{% if features.workspace_panel_metadata or features.workspace_widget_hosts %}
import ca.teamdman.sfm.client.screen.SFMScissorStack;
{% endif %}
{% if features.workspace_screen_diagnostics %}
import ca.teamdman.sfm.client.screen.SFMScreenDiagnosticsContributor;
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.input_diagnostics_panel %}
import ca.teamdman.sfm.client.screen.workspace.diagnostic.SFMInputDiagnosticsPanel;
{% endif %}
{% endif %}
{% if features.workspace_notifications %}
import ca.teamdman.sfm.client.screen.workspace.toast.SFMWorkspaceToastLayout;
{% endif %}
{% if features.workspace_notifications or features.workspace_toast_actions %}
import ca.teamdman.sfm.client.screen.workspace.toast.SFMWorkspaceToastQueue;
{% endif %}
{% if features.workspace_notifications %}
import ca.teamdman.sfm.client.screen.workspace.toast.SFMWorkspaceToastContent;
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.terminal_properties %}
import ca.teamdman.sfm.client.terminal.SFMTerminalPanel;
{% endif %}
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.terminal_properties %}
import ca.teamdman.sfm.client.terminal.SFMTerminalPropertiesPanel;
{% endif %}
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.client_overlay_scenes %}
import ca.teamdman.sfm.common.localization.LocalizationEntry;
{% endif %}
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.client_overlay_scenes %}
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
{% endif %}
{% endif %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% if features.workspace_panel_metadata or features.workspace_widget_hosts %}
{% else %}
import com.mojang.blaze3d.systems.RenderSystem;
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import com.mojang.blaze3d.vertex.PoseStack;
{% when "26.1.2" %}
import net.minecraft.client.gui.GuiGraphicsExtractor;
{% else %}
import net.minecraft.client.gui.GuiGraphics;
{% endcase %}
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
{% if features.workspace_widget_hosts %}
import net.minecraft.client.gui.narration.NarratedElementType;
{% endif %}
{% if features.workspace_widget_hosts %}
import net.minecraft.client.gui.narration.NarrationElementOutput;
{% endif %}
import net.minecraft.network.chat.Component;
{% if features.workspace_notifications %}
import net.minecraft.network.chat.Style;
{% endif %}
{% if features.workspace_keyboard_context or features.workspace_panel_actions or features.workspace_toast_actions or features.workspace_notifications %}
import net.minecraft.resources.ResourceLocation;
{% endif %}
{% if features.workspace_notifications %}
import net.minecraft.util.FormattedCharSequence;
{% endif %}
{% if features.workspace_panel_actions or features.workspace_toast_actions %}
import com.mojang.brigadier.exceptions.CommandSyntaxException;
{% endif %}
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
{% if features.workspace_focus_tracking %}
import java.util.ArrayDeque;
{% endif %}
{% if features.workspace_notifications or features.context_actions or features.workspace_screen_diagnostics or features.workspace_panel_entry_controls or features.workspace_panel_actions %}
import java.util.ArrayList;
{% endif %}
import java.util.HashSet;
{% if features.workspace_focus_tracking %}
import java.util.Iterator;
{% endif %}
import java.util.List;
import java.util.Map;
{% if features.workspace_directional_opening or features.workspace_focus_tracking or features.workspace_panel_entry_controls or features.workspace_panel_move_gestures or features.workspace_notifications or features.workspace_panel_metadata or features.workspace_stack_controls or features.workspace_panel_reopening or features.workspace_lifecycle or features.workspace_dividers or features.workspace_directional_resize or features.workspace_widget_hosts or features.workspace_keyboard_context or features.workspace_panel_measurement or features.workspace_panel_lookup or features.workspace_panel_actions or features.context_actions or features.workspace_screen_diagnostics %}
import java.util.Objects;
{% endif %}
{% if features.workspace_focus_tracking or features.workspace_panel_entry_controls or features.workspace_notifications or features.workspace_panel_reopening or features.workspace_stack_controls or features.workspace_keyboard_context or features.workspace_panel_measurement or features.workspace_panel_lookup or features.context_actions or features.workspace_screen_diagnostics or features.workspace_panel_tooltips or features.workspace_dividers %}
import java.util.Optional;
{% endif %}
import java.util.Set;
{% if features.workspace_notifications %}
import java.util.function.Consumer;
{% endif %}
{% if features.workspace_focus_tracking %}
import java.util.function.Predicate;
{% endif %}

/** Owns Minecraft's Screen lifecycle while hosting a normalized tree of SFM panels. */
{% if features.workspace_keyboard_context %}
{% if features.workspace_screen_diagnostics %}
public final class SFMScreenMultiplexer extends Screen implements SFMWorkspacePanelHost,
        SFMKeyboardUsageContextProvider, SFMScreenDiagnosticsContributor {
{% else %}
{% if features.workspace_keyboard_context %}
public final class SFMScreenMultiplexer extends Screen implements SFMWorkspacePanelHost,
        SFMKeyboardUsageContextProvider {
{% else %}
{% if features.workspace_screen_diagnostics %}
public final class SFMScreenMultiplexer extends Screen implements SFMWorkspacePanelHost,
        SFMScreenDiagnosticsContributor {
{% else %}
public final class SFMScreenMultiplexer extends Screen implements SFMWorkspacePanelHost {
{% endif %}
{% endif %}
{% endif %}
{% else %}
{% if features.workspace_keyboard_context %}
public final class SFMScreenMultiplexer extends Screen implements SFMWorkspacePanelHost,
        SFMKeyboardUsageContextProvider {
{% else %}
{% if features.workspace_screen_diagnostics %}
public final class SFMScreenMultiplexer extends Screen implements SFMWorkspacePanelHost,
        SFMScreenDiagnosticsContributor {
{% else %}
public final class SFMScreenMultiplexer extends Screen implements SFMWorkspacePanelHost {
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.client_overlay_scenes %}
    @SFMLocalizationDatagen
    public static final LocalizationEntry TOGGLE_FPS_OVERLAY = new LocalizationEntry(
            "gui.sfm.workspace.diagnostics.toggle_fps_overlay",
            "Toggle FPS overlay"
    );
{% endif %}
{% endif %}
    private static final int DIVIDER_WIDTH = 2;
{% if features.workspace_dividers %}
    private static final int DIVIDER_HIT_SLOP = 3;
{% endif %}
{% if features.workspace_dividers or features.workspace_directional_resize %}
    private static final int MINIMUM_PANEL_PIXELS = 48;
{% endif %}
    private static final int PANEL_BACKGROUND = 0xE0202020;
    private static final int FOCUSED_BORDER = 0xFF55FFFF;
    private static final int UNFOCUSED_BORDER = 0xFF606060;
{% if features.workspace_panel_actions %}
    private static final ResourceLocation OPEN_PANEL = new ResourceLocation(SFM.MOD_ID, "panel/open");
{% endif %}
{% if features.workspace_panel_actions %}
    private static final ResourceLocation OPEN_PANEL_LEFT = new ResourceLocation(SFM.MOD_ID, "panel/open/left");
{% endif %}
{% if features.workspace_panel_actions %}
    private static final ResourceLocation OPEN_PANEL_RIGHT = new ResourceLocation(SFM.MOD_ID, "panel/open/right");
{% endif %}
{% if features.workspace_panel_actions %}
    private static final ResourceLocation OPEN_PANEL_ABOVE = new ResourceLocation(SFM.MOD_ID, "panel/open/above");
{% endif %}
{% if features.workspace_panel_actions %}
    private static final ResourceLocation OPEN_PANEL_BELOW = new ResourceLocation(SFM.MOD_ID, "panel/open/below");
{% endif %}
{% if features.workspace_panel_actions %}
    private static final ResourceLocation CLOSE_PANEL = new ResourceLocation(SFM.MOD_ID, "panel/close");
{% endif %}
{% if features.workspace_panel_actions %}
    private static final ResourceLocation CLOSE_SCREEN = new ResourceLocation(SFM.MOD_ID, "screen/close");
{% endif %}
{% if features.workspace_panel_actions %}
    private static final ResourceLocation CLOSE_PALETTE = new ResourceLocation(SFM.MOD_ID, "palette/close");
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.client_overlay_scenes %}
    private static final ResourceLocation TOGGLE_OVERLAY_VISIBILITY =
            new ResourceLocation(SFM.MOD_ID, "overlay/visibility/toggle");
{% endif %}
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.client_overlay_scenes %}
    private static final String FPS_OVERLAY_SELECTOR = SFMEntitySelector.exact(
            SFMEntitySelector.Domain.OVERLAY,
            SFMOverlaySceneContract.FPS_OVERLAY_ID
    ).canonical();
{% endif %}
{% endif %}
{% if features.workspace_notifications or features.workspace_toast_actions %}
    private static final ResourceLocation COPY_TOAST = new ResourceLocation(SFM.MOD_ID, "toast/copy");
{% endif %}
{% if features.workspace_notifications or features.workspace_toast_actions %}
    private static final ResourceLocation STOP_TOAST_TIMER = new ResourceLocation(SFM.MOD_ID, "toast/timer/stop");
{% endif %}
{% if features.workspace_notifications or features.workspace_toast_actions %}
    private static final ResourceLocation RESUME_TOAST_TIMER = new ResourceLocation(SFM.MOD_ID, "toast/timer/resume");
{% endif %}
{% if features.workspace_notifications or features.workspace_toast_actions %}
    private static final ResourceLocation DISMISS_TOAST = new ResourceLocation(SFM.MOD_ID, "toast/dismiss");
{% endif %}
{% if features.workspace_notifications %}
{% if features.workspace_panel_metadata %}
    private static final String SCALE_TOAST_KEY = "sfm:panel-scale";
{% endif %}
{% endif %}
{% if features.workspace_notifications %}
    private static final String COPY_CONFIRMATION_TOAST_KEY = "sfm:clipboard-copy-confirmation";
{% endif %}
{% if features.workspace_notifications %}
    private static final String COPY_CONFIRMATION_ALTERNATE_TOAST_KEY =
            "sfm:clipboard-copy-confirmation-alternate";
{% endif %}
{% if features.workspace_notifications %}
    private static final int TOAST_MAX_LINES = 6;
{% endif %}
{% if features.workspace_focus_tracking %}
    private static final int MAX_FOCUS_HISTORY_ENTRIES = 32;
{% endif %}
    private final @Nullable Screen previousScreen;
    private final SFMWorkspaceLayout layout;
    private final @Nullable SFMWorkspacePanelGroup panelGroup;
    private final Set<SFMWorkspacePanelId> openedPanels = new HashSet<>();
{% if features.workspace_lifecycle %}
    private final Map<SFMWorkspacePanelId, SFMScreenPanel> openedPanelInstances = new java.util.HashMap<>();
{% endif %}
{% if features.workspace_panel_reopening %}
    private final SFMPanelReopenCatalog reopenRecipes = new SFMPanelReopenCatalog();
{% endif %}
    private Map<SFMWorkspacePanelId, SFMScreenPanelBounds> panelBounds = Map.of();
    private boolean closing;
    private @Nullable Component dropFeedback;
{% if features.workspace_notifications or features.workspace_toast_actions %}
    private @Nullable SFMWorkspaceToastQueue workspaceToasts = new SFMWorkspaceToastQueue();
{% endif %}
{% if features.workspace_notifications %}
    private @Nullable SFMWorkspaceToastLayout workspaceToastLayout = new SFMWorkspaceToastLayout();
{% endif %}
{% if features.workspace_notifications %}
    private List<ToastHitRegion> workspaceToastHitRegions = List.of();
{% endif %}
{% if features.workspace_notifications %}
    private Map<SFMWorkspaceToastQueue.ToastId, SFMWorkspaceToastContent> workspaceToastContents;
{% endif %}
{% if features.workspace_notifications %}
{% if features.workspace_toast_actions %}
    private Map<SFMWorkspaceToastQueue.ToastId, List<SFMActionChoice>> workspaceToastAdditionalActions;
{% endif %}
{% endif %}
{% if features.workspace_panel_entry_controls %}
    private List<SFMPanelEntryAffordanceLayout.HitRegion> panelEntryHitRegions = List.of();
{% endif %}
{% if features.workspace_notifications %}
    private @Nullable SFMWorkspaceToastQueue.ToastId capturedWorkspaceToastPointer;
{% endif %}
    private long panelGroupRevision = Long.MIN_VALUE;
{% if features.workspace_focus_tracking or features.workspace_keyboard_context or features.context_actions %}
    private @Nullable SFMWorkspacePanelId observedFocusedPanel;
{% endif %}
{% if features.workspace_focus_tracking %}
    private @Nullable ArrayDeque<FocusedPanelWitness> focusHistory;
{% endif %}
{% if features.workspace_focus_tracking or features.workspace_keyboard_context or features.context_actions or features.workspace_screen_diagnostics %}
    private long keyboardFocusRevision;
{% endif %}
{% if features.context_actions %}
    private long contextWorkspaceRevision;
{% endif %}
{% if features.context_actions %}
    private long contextCaptureGeneration;
{% endif %}
{% if features.workspace_dividers %}
    private @Nullable SFMWorkspaceDividerInteraction dividerInteraction;
{% endif %}
{% if features.workspace_panel_move_gestures %}
    private @Nullable SFMWorkspacePanelMoveInteraction panelMoveInteraction;
{% endif %}

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
{% if features.workspace_panel_reopening %}

    public static SFMScreenMultiplexer create(
            @Nullable Screen previousScreen,
            SFMScreenPanel initialPanel,
            SFMPanelReopenRecipe reopenRecipe
    ) {
        SFMScreenMultiplexer workspace = create(previousScreen, initialPanel);
        workspace.registerReopenRecipe(initialPanel, Objects.requireNonNull(reopenRecipe));
        return workspace;
    }
{% endif %}

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
{% if features.workspace_directional_opening %}

    public static void openToSide(@Nullable Screen origin, SFMScreenPanel panel) {
        openToSide(origin, SFMWorkspaceSide.RIGHT, panel);
    }
{% else %}

    public static void openToSide(@Nullable Screen origin, SFMScreenPanel panel) {
        if (origin instanceof SFMScreenMultiplexer multiplexer) {
            multiplexer.submit(
                    multiplexer.layout.focusedPanel(),
                    new SFMWorkspacePanelIntent.OpenToSide(SFMWorkspaceSide.RIGHT, panel)
            );
            return;
        }
        SFMScreenChangeHelpers.setScreen(new SFMScreenMultiplexer(
                origin,
                new SFMPreviousScreenPanel(origin),
                panel
        ));
    }
{% endif %}
{% if features.workspace_directional_opening %}

    public static void openToSide(
            @Nullable Screen origin,
            SFMWorkspaceSide side,
            SFMScreenPanel panel
    ) {
{% if features.workspace_panel_reopening %}
        openToSide(origin, side, panel, null);
{% else %}
        SFMScreenPanel previous = new SFMPreviousScreenPanel(origin);
        SFMScreenPanel first = side == SFMWorkspaceSide.LEFT || side == SFMWorkspaceSide.ABOVE ? panel : previous;
        SFMScreenPanel second = side == SFMWorkspaceSide.LEFT || side == SFMWorkspaceSide.ABOVE ? previous : panel;
        SFMWorkspaceLayout tree = side.axis() == SFMWorkspaceAxis.HORIZONTAL
                ? SFMWorkspaceLayout.group(SFMWorkspaceLayout.horizontal(SFMWorkspaceLayout.panel(first), SFMWorkspaceLayout.panel(second)))
                : SFMWorkspaceLayout.group(SFMWorkspaceLayout.vertical(SFMWorkspaceLayout.panel(first), SFMWorkspaceLayout.panel(second)));
        if (origin instanceof SFMScreenMultiplexer multiplexer) {
            multiplexer.openToSide(
                    multiplexer.layout.focusedPanel(), side, panel);
            return;
        }
        SFMScreenChangeHelpers.setScreen(SFMScreenMultiplexer.create(origin, tree));
{% endif %}
    }
{% endif %}
{% if features.workspace_directional_opening %}
{% if features.workspace_panel_reopening %}

    public static void openToSide(
            @Nullable Screen origin,
            SFMWorkspaceSide side,
            SFMScreenPanel panel,
            @Nullable SFMPanelReopenRecipe reopenRecipe
    ) {
        if (origin instanceof SFMScreenMultiplexer multiplexer) {
            multiplexer.openToSide(
                    multiplexer.layout.focusedPanel(), side, panel, reopenRecipe);
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
{% endif %}
{% endif %}
{% if features.workspace_stack_controls %}

    public static void openFocused(
            @Nullable Screen origin,
            SFMScreenPanel panel,
            SFMWorkspacePanelMetadata metadata
    ) {
{% if features.workspace_panel_reopening %}
        openFocused(origin, panel, metadata, null);
{% else %}
        if (origin instanceof SFMScreenMultiplexer multiplexer) {
            multiplexer.openFocused(panel, metadata);
            return;
        }
        SFMScreenChangeHelpers.setScreen(SFMScreenMultiplexer.create(origin, panel));
{% endif %}
    }
{% endif %}
{% if features.workspace_stack_controls %}
{% if features.workspace_panel_reopening %}

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
{% endif %}
{% endif %}

    public void openToSide(SFMScreenPanel panel) {
        submit(layout.focusedPanel(), new SFMWorkspacePanelIntent.OpenToSide(SFMWorkspaceSide.RIGHT, panel));
    }
{% if features.workspace_directional_opening %}

    public SFMWorkspacePanelIntentResult openToSide(
            SFMWorkspacePanelId source,
            SFMWorkspaceSide side,
            SFMScreenPanel panel
    ) {
{% if features.workspace_panel_reopening %}
        return openToSide(source, side, panel, null);
{% else %}
{% if features.workspace_panel_metadata %}
        return submit(source, new SFMWorkspacePanelIntent.OpenToSide(side, panel, SFMWorkspacePanelMetadata.ordinary()));
{% else %}
        return submit(source, new SFMWorkspacePanelIntent.OpenToSide(side, panel));
{% endif %}
{% endif %}
    }
{% endif %}
{% if features.workspace_directional_opening %}
{% if features.workspace_panel_reopening %}

    public SFMWorkspacePanelIntentResult openToSide(
            SFMWorkspacePanelId source,
            SFMWorkspaceSide side,
            SFMScreenPanel panel,
            @Nullable SFMPanelReopenRecipe reopenRecipe
    ) {
{% if features.workspace_panel_metadata %}
        return openToSide(
                source,
                side,
                panel,
                SFMWorkspacePanelMetadata.ordinary(),
                reopenRecipe
        );
{% else %}
        if (layout.panel(source) == null) return SFMWorkspacePanelIntentResult.UNAVAILABLE;
        return submit(source, new SFMWorkspacePanelIntent.OpenToSide(side, panel, reopenRecipe));
{% endif %}
    }
{% endif %}
{% endif %}
{% if features.workspace_directional_opening %}
{% if features.workspace_panel_metadata %}
{% if features.workspace_panel_reopening %}

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
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_stack_controls %}

    public SFMWorkspacePanelIntentResult openFocused(
            SFMScreenPanel panel,
            SFMWorkspacePanelMetadata metadata
    ) {
{% if features.workspace_panel_reopening %}
        return openFocused(panel, metadata, null);
{% else %}
        return submit(layout.focusedPanel(), new SFMWorkspacePanelIntent.OpenAsTab(panel, metadata));
{% endif %}
    }
{% endif %}
{% if features.workspace_stack_controls %}
{% if features.workspace_panel_reopening %}

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
{% endif %}
{% endif %}
{% if features.workspace_stack_controls %}

    public SFMWorkspacePanelIntentResult openIntoSlot(
            SFMWorkspacePanelId slot,
            SFMScreenPanel panel,
            SFMWorkspacePanelMetadata metadata
    ) {
{% if features.workspace_panel_reopening %}
        return openIntoSlot(slot, panel, metadata, null);
{% else %}
        if (layout.panel(slot) == null) return SFMWorkspacePanelIntentResult.UNAVAILABLE;
        return submit(slot, new SFMWorkspacePanelIntent.OpenAsTab(panel, metadata));
{% endif %}
    }
{% endif %}
{% if features.workspace_stack_controls %}
{% if features.workspace_panel_reopening %}

    public SFMWorkspacePanelIntentResult openIntoSlot(
            SFMWorkspacePanelId slot,
            SFMScreenPanel panel,
            SFMWorkspacePanelMetadata metadata,
            @Nullable SFMPanelReopenRecipe reopenRecipe
    ) {
        if (layout.panel(slot) == null) return SFMWorkspacePanelIntentResult.UNAVAILABLE;
        return submit(slot, new SFMWorkspacePanelIntent.OpenAsTab(panel, metadata, reopenRecipe));
    }
{% endif %}
{% endif %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_stack_controls %}

    public boolean focusPanel(SFMWorkspacePanelId panelId) {
        Objects.requireNonNull(panelId, "panelId");
{% if features.workspace_focus_tracking or features.workspace_keyboard_context or features.context_actions %}
        observeWorkspaceFocus();
{% endif %}
        if (panelId.equals(layout.focusedPanel())) {
            return layout.panel(panelId) != null;
        }
        boolean focused = layout.focus(panelId);
        if (focused) {
{% if features.workspace_focus_tracking or features.workspace_keyboard_context or features.context_actions %}
            observeWorkspaceFocus();
{% endif %}
            refreshLayout(true);
        }
        return focused;
    }
{% endif %}
{% if features.workspace_panel_actions %}

    /** Focuses a visible panel using the one-based index exposed to users. */
    public boolean focusVisiblePanel(int oneBasedIndex) {
{% if features.workspace_panel_metadata or features.workspace_stack_controls or features.workspace_dividers or features.workspace_directional_resize or features.workspace_layout_focus_fix %}
        List<SFMWorkspaceLayout.PanelEntry> panels = layout.visiblePanels();
{% else %}
        List<SFMWorkspaceLayout.PanelEntry> panels = layout.panels();
{% endif %}
        int index = oneBasedIndex - 1;
        if (index < 0 || index >= panels.size()) return false;
        return focusPanel(panels.get(index).id());
    }
{% endif %}
{% if features.workspace_panel_actions %}

    /** Applies the same traversal used by the Ctrl+Tab workspace shortcut. */
    public boolean traversePanelFocus(int direction) {
{% if features.workspace_focus_tracking or features.workspace_keyboard_context or features.context_actions %}
        observeWorkspaceFocus();
{% endif %}
{% if features.workspace_stack_controls %}
        boolean changed = layout.traverse(direction);
{% else %}
        List<SFMWorkspaceLayout.PanelEntry> panels = layout.panels();
        int current = focusedPanel();
        boolean changed = !panels.isEmpty() && layout.focus(panels.get(Math.floorMod(current + direction, panels.size())).id());
{% endif %}
        if (changed) {
{% if features.workspace_focus_tracking or features.workspace_keyboard_context or features.context_actions %}
            observeWorkspaceFocus();
{% endif %}
            refreshLayout(true);
{% if features.workspace_widget_hosts %}
            synchronizeWidgetHostActivation();
{% endif %}
        }
        return changed;
    }
{% endif %}
{% if features.workspace_panel_actions %}

    public boolean canToggleMaximize() {
        return panelGroup != null && focusedPanelInstance() != null;
    }
{% endif %}
{% if features.workspace_panel_actions %}

    /** Applies the same maximize behavior used by the Ctrl+M workspace shortcut. */
    public boolean toggleMaximizeFocusedPanel() {
        SFMScreenPanel focused = focusedPanelInstance();
        if (panelGroup == null || focused == null) return false;
        panelGroup.toggleMaximize(focused);
        refreshLayout(true);
        return true;
    }
{% endif %}
{% if features.workspace_panel_actions %}

    public boolean containsPanel(SFMWorkspacePanelId panelId) {
        return layout.panel(panelId) != null;
    }
{% endif %}
{% if features.workspace_panel_actions %}

    public SFMWorkspacePanelIntentResult closeFocused() {
        return submit(layout.focusedPanel(), new SFMWorkspacePanelIntent.Close());
    }
{% endif %}
{% if features.workspace_panel_actions %}

    public SFMWorkspacePanelIntentResult closePanel(SFMWorkspacePanelId panelId) {
        if (layout.panel(panelId) == null) return SFMWorkspacePanelIntentResult.UNAVAILABLE;
        return submit(panelId, new SFMWorkspacePanelIntent.Close());
    }
{% endif %}
{% if features.workspace_stack_controls %}

    public SFMWorkspacePanelIntentResult moveFocused(SFMWorkspaceSide side) {
        return submit(layout.focusedPanel(), new SFMWorkspacePanelIntent.Move(side));
    }
{% endif %}
{% if features.workspace_stack_controls %}

    /** Focuses and moves one exact entry; callers must perform any stronger capture validation first. */
    public SFMWorkspacePanelIntentResult movePanel(
            SFMWorkspacePanelId panelId,
            SFMWorkspaceSide side
    ) {
        Objects.requireNonNull(panelId, "panelId");
        Objects.requireNonNull(side, "side");
{% if features.workspace_focus_tracking or features.workspace_keyboard_context or features.context_actions %}
        if (!focusPanel(panelId)) return SFMWorkspacePanelIntentResult.UNAVAILABLE;
{% else %}
        if (!layout.focus(panelId)) return SFMWorkspacePanelIntentResult.UNAVAILABLE;
{% endif %}
        return submit(panelId, new SFMWorkspacePanelIntent.Move(side));
    }
{% endif %}
{% if features.workspace_stack_controls %}

    /** Moves one exact entry into the pane containing another exact entry. */
    public SFMWorkspacePanelIntentResult movePanelToStack(
            SFMWorkspacePanelId panelId,
            SFMWorkspacePanelId destination
    ) {
        Objects.requireNonNull(panelId, "panelId");
        Objects.requireNonNull(destination, "destination");
        return submit(panelId, new SFMWorkspacePanelIntent.MoveToStack(destination));
    }
{% endif %}
{% if features.workspace_panel_reopening %}

    public boolean canDuplicate(SFMWorkspacePanelId panelId) {
        return duplicateUnavailableReason(panelId).isEmpty();
    }
{% endif %}
{% if features.workspace_panel_reopening %}

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
{% endif %}
{% if features.workspace_panel_reopening %}

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
{% if features.workspace_panel_metadata %}
        SFMWorkspacePanelMetadata metadata = layout.metadata(panelId);
{% endif %}
        return submit(panelId, new SFMWorkspacePanelIntent.OpenToSide(
                side,
                duplicate,
{% if features.workspace_panel_metadata %}
                metadata == null ? SFMWorkspacePanelMetadata.ordinary() : metadata,
{% endif %}
                recipe));
    }
{% endif %}
{% if features.workspace_directional_resize %}

    public boolean canResizePanel(SFMWorkspacePanelId panelId, SFMWorkspaceSide side) {
        if (this.width <= 0 || this.height <= 0) return false;
        return layout.canResize(
                panelId,
                side,
                new SFMScreenPanelBounds(0, 0, this.width, this.height),
                DIVIDER_WIDTH,
                MINIMUM_PANEL_PIXELS);
    }
{% endif %}
{% if features.workspace_directional_resize %}

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
{% endif %}
{% if features.workspace_panel_reopening %}

    public Optional<SFMPanelReopenRecipe> reopenRecipe(SFMWorkspacePanelId panelId) {
        SFMScreenPanel panel = layout.panel(panelId);
        return panel == null ? Optional.empty() : reopenRecipes.recipeFor(panel);
    }
{% endif %}
{% if features.workspace_panel_reopening %}

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
{% endif %}
{% if features.workspace_panel_metadata %}

    public boolean setFocusedGuiScale(@Nullable Integer scale) {
        boolean changed = layout.setFocusedGuiScale(scale);
        if (changed) refreshLayout(true);
        return changed;
    }
{% endif %}
{% if features.workspace_panel_metadata %}

    public boolean setPanelGuiScale(SFMWorkspacePanelId panelId, @Nullable Integer scale) {
        boolean changed = layout.setGuiScale(panelId, scale);
        if (changed) refreshLayout(true);
        return changed;
    }
{% endif %}
{% if features.workspace_stack_controls %}

    public List<SFMWorkspaceLayout.PanelEntry> panelSlotEntries(SFMWorkspacePanelId panelId) {
        return layout.slotEntries(panelId);
    }
{% endif %}
{% if features.workspace_lifecycle %}
{% if features.workspace_stack_controls %}

    /** Captures one pane, including exact panel object identities and close-risk counts. */
    public Optional<SFMWorkspacePaneCloseCapture> capturePaneClose(SFMWorkspacePanelId panelId) {
        if (panelId == null || panelGroup != null) return Optional.empty();
        Optional<SFMWorkspaceStackId> paneId = layout.stackId(panelId);
        List<SFMWorkspaceLayout.PanelEntry> entries = layout.slotEntries(panelId);
        if (paneId.isEmpty() || entries.isEmpty()) return Optional.empty();
        List<SFMWorkspacePaneCloseCapture.EntryWitness> witnesses = entries.stream()
                .map(entry -> new SFMWorkspacePaneCloseCapture.EntryWitness(entry.id(), entry.panel()))
                .toList();
        return Optional.of(new SFMWorkspacePaneCloseCapture(
                paneId.orElseThrow(),
                witnesses,
                paneCloseSummary(entries)
        ));
    }
{% endif %}
{% endif %}
{% if features.workspace_lifecycle %}
{% if features.workspace_stack_controls %}

    /** True only while every captured id still denotes the exact same pane member and state. */
    public boolean matchesPaneCloseCapture(SFMWorkspacePaneCloseCapture capture) {
        Objects.requireNonNull(capture, "capture");
        if (panelGroup != null || capture.entries().isEmpty()) return false;
        SFMWorkspacePanelId anchor = capture.entries().get(0).id();
        if (!layout.stackId(anchor).filter(capture.paneId()::equals).isPresent()) return false;
        List<SFMWorkspaceLayout.PanelEntry> current = layout.slotEntries(anchor);
        if (current.size() != capture.entries().size()) return false;
        for (int index = 0; index < current.size(); index++) {
            SFMWorkspaceLayout.PanelEntry entry = current.get(index);
            SFMWorkspacePaneCloseCapture.EntryWitness witness = capture.entries().get(index);
            if (!entry.id().equals(witness.id()) || entry.panel() != witness.panel()) return false;
        }
        return paneCloseSummary(current).equals(capture.summary());
    }
{% endif %}
{% endif %}
{% if features.workspace_lifecycle %}
{% if features.workspace_stack_controls %}

    /** Atomically validates and removes every entry in one captured pane. */
    public SFMWorkspacePanelIntentResult closePane(SFMWorkspacePaneCloseCapture capture) {
        if (!matchesPaneCloseCapture(capture)) return SFMWorkspacePanelIntentResult.UNAVAILABLE;
        List<SFMWorkspacePaneCloseCapture.EntryWitness> entries = capture.entries();
        for (SFMWorkspacePaneCloseCapture.EntryWitness entry : entries) {
            if (!layout.remove(entry.id())) return SFMWorkspacePanelIntentResult.UNAVAILABLE;
        }
        for (SFMWorkspacePaneCloseCapture.EntryWitness entry : entries) {
{% if features.workspace_widget_hosts %}
            entry.panel().widgetHost().ifPresent(SFMPanelWidgetHost::closed);
{% endif %}
            entry.panel().closed();
            openedPanels.remove(entry.id());
{% if features.workspace_lifecycle %}
            openedPanelInstances.remove(entry.id());
{% endif %}
{% if features.workspace_panel_reopening %}
            reopenRecipes.remove(entry.panel());
{% endif %}
        }
        if (layout.panels().isEmpty()) onClose();
        else refreshLayout(true);
        return SFMWorkspacePanelIntentResult.APPLIED;
    }
{% endif %}
{% endif %}
{% if features.workspace_lifecycle %}
{% if features.workspace_stack_controls %}

    private SFMWorkspacePaneCloseSummary paneCloseSummary(
            List<SFMWorkspaceLayout.PanelEntry> entries
    ) {
        int dirty = 0;
        int readOnly = 0;
        int recoverable = 0;
        for (SFMWorkspaceLayout.PanelEntry entry : entries) {
            SFMPanelCloseState state = entry.panel().closeState();
            if (state.dirty()) dirty++;
            if (state.readOnly()) readOnly++;
{% if features.workspace_panel_reopening %}
            if (reopenRecipes.contains(entry.panel())) recoverable++;
{% endif %}
        }
        return new SFMWorkspacePaneCloseSummary(
                entries.size(),
                dirty,
                readOnly,
                recoverable,
                entries.size() - recoverable
        );
    }
{% endif %}
{% endif %}
{% if features.workspace_stack_controls %}

    public boolean rotateVisibleContent(int direction) {
        boolean changed = layout.rotateVisibleContent(direction);
        if (changed) refreshLayout(true);
        return changed;
    }
{% endif %}
{% if features.workspace_stack_controls %}

    public boolean rotateVisibleScale(int direction) {
        boolean changed = layout.rotateVisibleScale(direction);
        if (changed) refreshLayout(true);
        return changed;
    }
{% endif %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_stack_controls %}

    public List<SFMWorkspaceLayout.PanelEntry> visiblePanelEntries() {
{% if features.workspace_panel_metadata or features.workspace_stack_controls or features.workspace_dividers or features.workspace_directional_resize or features.workspace_layout_focus_fix %}
        return layout.visiblePanels();
{% else %}
        return layout.panels();
{% endif %}
    }
{% endif %}
{% if features.workspace_stack_controls %}

    public List<SFMWorkspaceLayout.PanelEntry> focusedSlotEntries() {
        return layout.focusedSlotEntries();
    }
{% endif %}
{% if features.workspace_focus_tracking %}

    /**
     * Returns the newest still-current panel identity matching {@code predicate}.
     * Reused panel ids never inherit an earlier panel object's focus history.
     */
    public Optional<SFMWorkspaceLayout.PanelEntry> mostRecentlyFocusedPanel(
            Predicate<SFMScreenPanel> predicate,
            @Nullable SFMWorkspacePanelId excluded
    ) {
        Objects.requireNonNull(predicate, "predicate");
        observeWorkspaceFocus();
        Iterator<FocusedPanelWitness> iterator = focusHistory().iterator();
        while (iterator.hasNext()) {
            FocusedPanelWitness witness = iterator.next();
            SFMScreenPanel current = layout.panel(witness.id());
            if (current != witness.panel()) {
                iterator.remove();
                continue;
            }
            if (witness.id().equals(excluded) || !predicate.test(current)) continue;
{% if features.workspace_panel_metadata %}
            SFMWorkspaceLayout.PanelEntry entry = layout.entry(witness.id());
{% else %}
            SFMWorkspaceLayout.PanelEntry entry = layout.panels().stream()
                    .filter(candidate -> candidate.id().equals(witness.id())).findFirst().orElse(null);
{% endif %}
            if (entry != null) return Optional.of(entry);
        }
        return Optional.empty();
    }
{% endif %}
{% if features.workspace_panel_entry_controls %}

    /** Last painted numbered-entry geometry in unscaled workspace GUI coordinates. */
    public List<SFMPanelEntryAffordanceLayout.HitRegion> panelEntryHitRegions() {
        return panelEntryHitRegions == null ? List.of() : List.copyOf(panelEntryHitRegions);
    }
{% endif %}

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
{% if features.workspace_focus_tracking or features.workspace_keyboard_context or features.context_actions %}

    /** Current keyboard-focus revision without forcing a full context capture. */
    public long keyboardFocusGeneration() {
        observeWorkspaceFocus();
        return keyboardFocusRevision;
    }
{% endif %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    /** Exact visible panel targeted by focused-panel actions. */
    public @Nullable SFMScreenPanel focusedPanelInstance() {
        return layout.panel(layout.focusedPanel());
    }
{% endif %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_panel_reopening or features.workspace_toast_path_actions %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_panel_reopening or features.workspace_notifications %}

    public @Nullable SFMScreenPanel panelInstance(SFMWorkspacePanelId panelId) {
        return layout.panel(panelId);
    }
{% endif %}
{% endif %}
{% if features.workspace_panel_metadata %}

    /** Host-owned metadata for an exact panel entry, including preview ownership. */
    public @Nullable SFMWorkspacePanelMetadata panelMetadata(SFMWorkspacePanelId panelId) {
        return layout.metadata(panelId);
    }
{% endif %}

    public List<SFMScreenPanel> panels() {
        return layout.panels().stream().map(SFMWorkspaceLayout.PanelEntry::panel).toList();
    }
{% if features.context_actions %}

    /** Captures every relevant visible panel independently; hidden tab entries are not guessed into context. */
    public SFMContextSnapshot contextSnapshot() {
        observeWorkspaceFocus();
        contextCaptureGeneration = incrementContextGeneration(contextCaptureGeneration);
        return captureVisibleContext(
{% if features.workspace_panel_metadata or features.workspace_stack_controls or features.workspace_dividers or features.workspace_directional_resize or features.workspace_layout_focus_fix %}
                layout.visiblePanels(),
{% else %}
                layout.panels(),
{% endif %}
                layout.panel(layout.focusedPanel()),
                contextCaptureGeneration,
                contextWorkspaceRevision,
                keyboardFocusRevision
        );
    }
{% endif %}
{% if features.context_actions %}

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
{% endif %}

    public List<SFMWorkspacePanelId> panelIds() {
        return layout.panels().stream().map(SFMWorkspaceLayout.PanelEntry::id).toList();
    }
{% if features.workspace_stack_controls or features.workspace_dividers %}

    public Optional<SFMWorkspaceStackId> panelStackId(SFMWorkspacePanelId panelId) {
        return layout.stackId(panelId);
    }
{% endif %}

    public @Nullable SFMScreenPanelBounds panelBounds(SFMWorkspacePanelId panelId) {
        return panelBounds.get(panelId);
    }
{% if features.workspace_screen_diagnostics %}

    @Override
    public List<String> screenDiagnostics() {
        ArrayList<String> diagnostics = new ArrayList<>();
        diagnostics.add("workspace.previous-screen=" + (previousScreen == null
                ? "none"
                : previousScreen.getClass().getName()));
        diagnostics.add("workspace.closing=" + closing);
        diagnostics.add("workspace.panel-count=" + layout.panels().size());
        diagnostics.add("workspace.focused-panel=" + focusedPanelId());
        diagnostics.add("workspace.keyboard-focus-generation=" + keyboardFocusRevision);
        for (SFMWorkspaceLayout.PanelEntry entry : layout.panels()) {
            String prefix = "workspace.panel[" + entry.id() + "].";
            diagnostics.add(prefix + "class=" + entry.panel().getClass().getName());
            diagnostics.add(prefix + "title=" + entry.panel().title().getString());
            diagnostics.add(prefix + "bounds=" + panelBounds(entry.id()));
            diagnostics.add(prefix + "content-bounds=" + panelContentBounds(entry.id()));
{% if features.workspace_stack_controls or features.workspace_dividers %}
            diagnostics.add(prefix + "stack=" + panelStackId(entry.id()).map(Object::toString).orElse("none"));
{% endif %}
            diagnostics.add(prefix + "focused=" + entry.id().equals(focusedPanelId()));
        }
        return List.copyOf(diagnostics);
    }
{% endif %}
{% if features.workspace_panel_metadata or features.workspace_widget_hosts or features.workspace_panel_measurement or features.workspace_screen_diagnostics %}

    /** Returns the logical content allocation after applying the entry's render scale. */
    public @Nullable SFMScreenPanelBounds panelContentBounds(SFMWorkspacePanelId panelId) {
{% if features.workspace_panel_metadata %}
        SFMWorkspaceLayout.PanelEntry entry = layout.entry(panelId);
{% else %}
        SFMWorkspaceLayout.PanelEntry entry = layout.panels().stream()
                .filter(candidate -> candidate.id().equals(panelId)).findFirst().orElse(null);
{% endif %}
{% if features.workspace_panel_metadata or features.workspace_widget_hosts or features.workspace_panel_measurement %}
        return entry == null ? null : contentBounds(entry);
{% else %}
        return entry == null ? null : contentBounds(panelId);
{% endif %}
    }
{% endif %}
{% if features.workspace_dividers %}

    public List<SFMWorkspaceDivider> dividerDescriptions() {
        if (this.width <= 0 || this.height <= 0) return List.of();
        return layout.dividers(
                workspaceViewport(),
                DIVIDER_WIDTH,
                DIVIDER_HIT_SLOP,
                MINIMUM_PANEL_PIXELS);
    }
{% endif %}
{% if features.workspace_dividers %}

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
{% endif %}
{% if features.workspace_dividers %}

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
{% endif %}
{% if features.workspace_dividers %}

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
{% endif %}

    @Override
    public SFMWorkspacePanelIntentResult submit(
            SFMWorkspacePanelId source,
            SFMWorkspacePanelIntent intent
    ) {
{% if features.workspace_panel_reopening %}
        @Nullable SFMPanelReopenRecipe insertedRecipe = null;
        if (intent instanceof SFMWorkspacePanelIntent.OpenToSide open) {
            insertedRecipe = open.reopenRecipe();
{% if features.workspace_stack_controls %}
        } else if (intent instanceof SFMWorkspacePanelIntent.OpenAsTab open) {
            insertedRecipe = open.reopenRecipe();
{% endif %}
        }
{% endif %}
        SFMWorkspacePanelIntentDispatcher.Outcome outcome =
                SFMWorkspacePanelIntentDispatcher.apply(layout, source, intent);
        if (outcome.result() != SFMWorkspacePanelIntentResult.APPLIED) return outcome.result();
{% if features.workspace_stack_controls %}
        if (outcome.moved()) {
            refreshLayout(true);
            return SFMWorkspacePanelIntentResult.APPLIED;
        }
{% endif %}
        if (outcome.inserted() != null) {
{% if features.workspace_panel_reopening %}
            registerReopenRecipe(layout.panel(outcome.inserted()), insertedRecipe);
{% endif %}
            refreshLayout(true);
            return SFMWorkspacePanelIntentResult.APPLIED;
        }
{% if features.workspace_widget_hosts %}
        outcome.removedPanel().widgetHost().ifPresent(SFMPanelWidgetHost::closed);
{% endif %}
        outcome.removedPanel().closed();
        openedPanels.remove(outcome.removed());
{% if features.workspace_lifecycle %}
        openedPanelInstances.remove(outcome.removed());
{% endif %}
{% if features.workspace_panel_reopening %}
        reopenRecipes.remove(outcome.removedPanel());
{% endif %}
        if (layout.panels().isEmpty()) {
            onClose();
        } else {
            refreshLayout(true);
        }
        return SFMWorkspacePanelIntentResult.APPLIED;
    }
{% if features.workspace_panel_measurement %}

    @Override
    public Optional<SFMWorkspacePanelMetrics> measure(
            SFMWorkspacePanelId source,
            SFMScreenPanelBounds logicalBounds
    ) {
{% if features.workspace_panel_metadata %}
        SFMWorkspaceLayout.PanelEntry entry = layout.entry(source);
{% else %}
        SFMWorkspaceLayout.PanelEntry entry = layout.panels().stream()
                .filter(candidate -> candidate.id().equals(source)).findFirst().orElse(null);
{% endif %}
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
{% if features.workspace_panel_metadata %}
                entry.metadata().guiScaleOverride() == null ? 0 : entry.metadata().guiScaleOverride(),
{% else %}
                0,
{% endif %}
                window.getWidth(),
                window.getHeight(),
                guiWidth,
                guiHeight
        ));
    }
{% endif %}
{% if features.workspace_panel_lookup %}

    @Override
    public Optional<SFMScreenPanel> panel(SFMWorkspacePanelId panelId) {
        return Optional.ofNullable(layout.panel(panelId));
    }
{% endif %}

    @Override
    protected void init() {
{% if features.workspace_dividers %}
        disposeDividerInteraction();
{% endif %}
{% if features.workspace_panel_move_gestures %}
        disposePanelMoveInteraction();
{% endif %}
{% if features.workspace_notifications %}
        workspaceToastHitRegions = List.of();
{% endif %}
{% if features.workspace_panel_entry_controls %}
        panelEntryHitRegions = List.of();
{% endif %}
{% if features.workspace_notifications %}
        capturedWorkspaceToastPointer = null;
{% endif %}
        super.init();
        refreshLayout(true);
{% if features.workspace_dividers %}
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
{% endif %}
{% if features.workspace_panel_move_gestures %}
        panelMoveInteraction = createPanelMoveInteraction();
{% endif %}
    }
{% if features.workspace_lifecycle or features.workspace_stack_controls or features.workspace_dividers or features.workspace_directional_resize or features.workspace_widget_hosts or features.workspace_panel_metadata or features.context_actions or features.workspace_focus_tracking or features.workspace_keyboard_context or features.workspace_panel_entry_controls or features.workspace_panel_measurement %}

    private void refreshLayout(boolean notifyPanels) {
        refreshLayout(notifyPanels, true);
    }
{% else %}

    private void refreshLayout(boolean notifyPanels) {
        if (panelGroup != null) {
            layout.recompose(panelGroup.layout(new SFMScreenPanelBounds(0, 0, this.width, this.height)));
            panelGroupRevision = panelGroup.revision();
        }
        panelBounds = layout.bounds(new SFMScreenPanelBounds(0, 0, this.width, this.height), DIVIDER_WIDTH);
        if (!notifyPanels || this.minecraft == null) return;
        for (SFMWorkspaceLayout.PanelEntry entry : layout.panels()) {
            if (openedPanels.contains(entry.id())) {
                entry.panel().resized(this.minecraft, contentBounds(entry.id()));
            } else {
                openPanel(entry.id(), entry.panel());
            }
        }
    }
{% endif %}
{% if features.workspace_lifecycle or features.workspace_stack_controls or features.workspace_dividers or features.workspace_directional_resize or features.workspace_widget_hosts or features.workspace_panel_metadata or features.context_actions or features.workspace_focus_tracking or features.workspace_keyboard_context or features.workspace_panel_entry_controls or features.workspace_panel_measurement %}

    private void refreshLayout(boolean notifyPanels, boolean recomposePanelGroup) {
{% if features.context_actions %}
        contextWorkspaceRevision = incrementContextGeneration(contextWorkspaceRevision);
{% endif %}
{% if features.workspace_panel_entry_controls %}
        panelEntryHitRegions = List.of();
{% endif %}
        if (panelGroup != null && recomposePanelGroup) {
            layout.recompose(panelGroup.layout(new SFMScreenPanelBounds(0, 0, this.width, this.height)));
            panelGroupRevision = panelGroup.revision();
        }
{% if features.workspace_dividers %}
        if (dividerInteraction != null) dividerInteraction.synchronizeLayoutRevision();
{% endif %}
        panelBounds = layout.bounds(new SFMScreenPanelBounds(0, 0, this.width, this.height), DIVIDER_WIDTH);
{% if features.workspace_widget_hosts %}
        synchronizeWidgetHostActivation();
{% endif %}
{% if features.workspace_focus_tracking or features.workspace_keyboard_context or features.context_actions %}
        observeWorkspaceFocus();
{% endif %}
        if (!notifyPanels || this.minecraft == null) return;
{% if features.workspace_stack_controls %}
        // A hidden stack entry remains a live panel. Closing it here destroys
        // panel-local state (notably its PTY and selected transport) merely
        // because another entry became visible in the same slot. Panels close
        // only when removed from the layout or when the workspace itself closes.
{% endif %}
{% if features.workspace_panel_metadata or features.workspace_stack_controls or features.workspace_dividers or features.workspace_directional_resize or features.workspace_layout_focus_fix %}
        for (SFMWorkspaceLayout.PanelEntry entry : layout.visiblePanels()) {
{% else %}
        for (SFMWorkspaceLayout.PanelEntry entry : layout.panels()) {
{% endif %}
            if (openedPanels.contains(entry.id())) {
{% if features.workspace_lifecycle %}
                SFMScreenPanel opened = openedPanelInstances.get(entry.id());
                if (opened != entry.panel()) {
                    if (opened != null) {
{% if features.workspace_widget_hosts %}
                        opened.widgetHost().ifPresent(SFMPanelWidgetHost::closed);
{% endif %}
                        opened.closed();
                    }
                    openPanel(entry.id(), entry.panel());
                } else {
{% if features.workspace_panel_metadata or features.workspace_widget_hosts or features.workspace_panel_measurement %}
                    entry.panel().resized(this.minecraft, contentBounds(entry));
{% else %}
                    entry.panel().resized(this.minecraft, contentBounds(entry.id()));
{% endif %}
                }
{% else %}
{% if features.workspace_panel_metadata or features.workspace_widget_hosts or features.workspace_panel_measurement %}
                entry.panel().resized(this.minecraft, contentBounds(entry));
{% else %}
                entry.panel().resized(this.minecraft, contentBounds(entry.id()));
{% endif %}
{% endif %}
            } else {
                openPanel(entry.id(), entry.panel());
            }
        }
    }
{% endif %}
{% if features.workspace_dividers or features.workspace_directional_resize %}

    private SFMScreenPanelBounds workspaceViewport() {
        return new SFMScreenPanelBounds(0, 0, Math.max(0, this.width), Math.max(0, this.height));
    }
{% endif %}
{% if features.workspace_dividers %}

    private void disposeDividerInteraction() {
        SFMWorkspaceDividerInteraction interaction = dividerInteraction;
        dividerInteraction = null;
        if (interaction != null) interaction.close();
    }
{% endif %}
{% if features.workspace_panel_move_gestures %}

    private void disposePanelMoveInteraction() {
        SFMWorkspacePanelMoveInteraction interaction = panelMoveInteraction;
        panelMoveInteraction = null;
        if (interaction != null) interaction.cancel();
    }
{% endif %}
{% if features.context_actions %}

    private static long incrementContextGeneration(long value) {
        return value == Long.MAX_VALUE ? value : value + 1;
    }
{% endif %}

    private void openPanel(SFMWorkspacePanelId id, SFMScreenPanel panel) {
{% if features.workspace_panel_metadata or features.workspace_widget_hosts or features.workspace_panel_measurement %}
        SFMWorkspaceLayout.PanelEntry entry = layout.panels().stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst().orElseThrow();
{% endif %}
{% if features.workspace_panel_metadata or features.workspace_widget_hosts or features.workspace_panel_measurement %}
        panel.opened(this.minecraft, contentBounds(entry), new SFMWorkspacePanelContext(id, this));
{% else %}
        panel.opened(this.minecraft, contentBounds(id), new SFMWorkspacePanelContext(id, this));
{% endif %}
        openedPanels.add(id);
{% if features.workspace_lifecycle %}
        openedPanelInstances.put(id, panel);
{% endif %}
    }
{% if features.workspace_panel_reopening %}

    private void registerReopenRecipe(
            @Nullable SFMScreenPanel panel,
            @Nullable SFMPanelReopenRecipe reopenRecipe
    ) {
        if (panel != null && reopenRecipe != null) reopenRecipes.register(panel, reopenRecipe);
    }
{% endif %}

    @Override
    public void tick() {
        if (panelGroup != null && panelGroupRevision != panelGroup.revision()) refreshLayout(true);
{% if features.workspace_dividers %}
        if (dividerInteraction != null && this.minecraft != null
                && GLFW.glfwGetWindowAttrib(
                this.minecraft.getWindow().getWindow(), GLFW.GLFW_FOCUSED) != GLFW.GLFW_TRUE) {
            dividerInteraction.focusLost();
{% if features.workspace_panel_move_gestures %}
            if (panelMoveInteraction != null) panelMoveInteraction.cancel();
{% endif %}
        }
{% endif %}
{% if features.workspace_panel_metadata or features.workspace_stack_controls or features.workspace_dividers or features.workspace_directional_resize or features.workspace_layout_focus_fix %}
        for (SFMWorkspaceLayout.PanelEntry entry : layout.visiblePanels()) entry.panel().tick();
{% else %}
        for (SFMWorkspaceLayout.PanelEntry entry : layout.panels()) entry.panel().tick();
{% endif %}
{% if features.java_symbols %}
        ca.teamdman.sfm.client.symbol.SFMFindReferencesController.tickProduction(this);
{% endif %}
{% if features.workspace_notifications or features.workspace_toast_actions %}
        toastQueue().tick();
{% endif %}
    }
{% if features.workspace_notifications %}
{% if features.workspace_panel_metadata %}

    /** Shows the current panel scale without permanently occupying panel space. */
    public void showGuiScaleToast(@Nullable Integer override, int inheritedScale, boolean shake) {
        String label = override == null
                ? "gui scale auto (" + inheritedScale + ")"
                : "gui scale " + override;
        toastQueue().publish(
                SCALE_TOAST_KEY,
                label,
                SFMWorkspaceToastQueue.Presentation.workspaceStatus(shake)
        );
    }
{% endif %}
{% endif %}
{% if features.workspace_notifications %}

    /** Shared status surface for producers that intentionally replace the generic status lane. */
    public SFMWorkspaceToastQueue.ToastId showWorkspaceToast(Component message, boolean shake) {
        return showWorkspaceToast("sfm:workspace-status", message, shake);
    }
{% endif %}
{% if features.workspace_lifecycle or features.workspace_notifications or features.workspace_panel_actions %}

    public boolean isClosing() {
        return closing;
    }
{% endif %}
{% if features.workspace_notifications %}
{% if features.workspace_toast_actions %}

    /** Explicit producer-supplied canonical actions, never commands parsed from notification text. */
    public SFMWorkspaceToastQueue.ToastId showWorkspaceToast(
            String replacementKey, Component message, boolean shake, List<SFMActionChoice> actions
    ) {
        var captured = List.copyOf(actions);
        var id = showWorkspaceToast(replacementKey, message, shake);
        if (workspaceToastAdditionalActions == null) workspaceToastAdditionalActions = new java.util.HashMap<>();
        workspaceToastAdditionalActions.keySet().retainAll(toastQueue().activeIds());
        workspaceToastAdditionalActions.put(id, captured);
        return id;
    }
{% endif %}
{% endif %}
{% if features.workspace_notifications %}

    /** Publishes or replaces one producer-owned status lane while retaining other queued messages. */
    public SFMWorkspaceToastQueue.ToastId showWorkspaceToast(
            String replacementKey,
            Component message,
            boolean shake
    ) {
        SFMWorkspaceToastContent captured = SFMWorkspaceToastContent.capture(
                Objects.requireNonNull(message, "message"));
        SFMWorkspaceToastQueue.ToastId id = toastQueue().publish(
                replacementKey,
                captured.component().getString(),
                SFMWorkspaceToastQueue.Presentation.actionableStatus(shake)
        );
        toastContents().put(id, captured);
        return id;
    }
{% endif %}
{% if features.workspace_notifications %}

    private Map<SFMWorkspaceToastQueue.ToastId, SFMWorkspaceToastContent> toastContents() {
        if (workspaceToastContents == null) workspaceToastContents = new java.util.HashMap<>();
        workspaceToastContents.keySet().retainAll(toastQueue().activeIds());
        return workspaceToastContents;
    }
{% endif %}
{% if features.workspace_notifications %}
{% if features.workspace_toast_path_actions %}

    public List<SFMPath> workspaceToastPaths(SFMWorkspaceToastQueue.ToastId id) {
        SFMWorkspaceToastContent content = toastContents().get(id);
        return content == null ? List.of() : content.paths();
    }
{% endif %}
{% endif %}
{% if features.workspace_notifications %}

    /** Extra information belongs to the exact toast, never its replacement lane. */
    public void attachWorkspaceToastDetails(SFMWorkspaceToastQueue.ToastId id, String details) {
        var content = toastContents().get(id);
        if (content != null) toastContents().put(id, content.withDetails(details));
    }
{% endif %}
{% if features.workspace_notifications %}

    public boolean copyWorkspaceToastDetails(SFMWorkspaceToastQueue.ToastId id) {
        return minecraft != null && copyWorkspaceToastDetails(id, this::writeVerifiedClipboard);
    }
{% endif %}
{% if features.workspace_notifications %}

    boolean copyWorkspaceToastDetails(SFMWorkspaceToastQueue.ToastId id, Consumer<String> clipboard) {
        var content = toastContents().get(id);
        if (content == null || content.details().isEmpty()) return false;
        if (!tryClipboardWrite(id, content.details().orElseThrow(), clipboard)) return false;
        toastQueue().publishPreserving(id, COPY_CONFIRMATION_TOAST_KEY, "Copied notification details to the clipboard",
                SFMWorkspaceToastQueue.Presentation.workspaceStatus(false));
        return true;
    }
{% endif %}
{% if features.workspace_notifications %}
{% if features.workspace_toast_path_actions %}

    public boolean copyWorkspaceToastPath(SFMWorkspaceToastQueue.ToastId id, int index) {
        return minecraft != null && copyWorkspaceToastPath(id, index, this::writeVerifiedClipboard);
    }
{% endif %}
{% endif %}
{% if features.workspace_notifications %}
{% if features.workspace_toast_path_actions %}

    boolean copyWorkspaceToastPath(SFMWorkspaceToastQueue.ToastId id, int index, Consumer<String> clipboard) {
        List<SFMPath> paths = workspaceToastPaths(id);
        if (index < 0 || index >= paths.size()) return false;
        if (!tryClipboardWrite(id, paths.get(index).canonical(), clipboard)) return false;
        toastQueue().publishPreserving(id, COPY_CONFIRMATION_TOAST_KEY, "Copied full path to the clipboard",
                SFMWorkspaceToastQueue.Presentation.workspaceStatus(false));
        return true;
    }
{% endif %}
{% endif %}
{% if features.workspace_notifications %}

    /** Publishes into the bounded clipboard-confirmation lane without replacing workspace status. */
    public SFMWorkspaceToastQueue.ToastId showClipboardCopyConfirmation(Component message) {
        return showWorkspaceToast(COPY_CONFIRMATION_TOAST_KEY, message, false);
    }
{% endif %}
{% if features.workspace_notifications or features.workspace_toast_actions %}

    public List<SFMWorkspaceToastQueue.ToastId> activeWorkspaceToastIds() {
        return toastQueue().activeIds();
    }
{% endif %}
{% if features.workspace_notifications %}

    public Optional<SFMWorkspaceToastQueue.Snapshot> workspaceToastSnapshot(
            SFMWorkspaceToastQueue.ToastId id
    ) {
        return toastQueue().snapshot(id);
    }
{% endif %}
{% if features.workspace_notifications %}

    public Optional<SFMWorkspaceToastQueue.Snapshot> latestWorkspaceToast() {
        return toastQueue().latestSnapshot();
    }
{% endif %}
{% if features.workspace_notifications %}

    /**
     * Returns the last painted hit target for one live toast. The surface is
     * intentionally read-only so puppet/assistive input can exercise the same
     * pointer route as a user without duplicating layout calculations.
     */
    public Optional<SFMWorkspaceToastLayout.Bounds> workspaceToastBounds(
            SFMWorkspaceToastQueue.ToastId id
    ) {
        Objects.requireNonNull(id, "id");
        List<ToastHitRegion> hitRegions = workspaceToastHitRegions;
        if (hitRegions == null) return Optional.empty();
        return hitRegions.stream()
                .filter(region -> region.id().equals(id))
                .map(ToastHitRegion::bounds)
                .findFirst();
    }
{% endif %}
{% if features.workspace_notifications %}

    public boolean copyWorkspaceToast(SFMWorkspaceToastQueue.ToastId id) {
        if (this.minecraft == null) return false;
        return copyWorkspaceToast(id, this::writeVerifiedClipboard);
    }
{% endif %}
{% if features.workspace_notifications %}

    private void writeVerifiedClipboard(String text) {
        verifyClipboardWrite(text, minecraft.keyboardHandler::setClipboard,
                minecraft.keyboardHandler::getClipboard);
    }
{% endif %}
{% if features.workspace_notifications %}

    static void verifyClipboardWrite(String text, Consumer<String> writer,
                                     java.util.function.Supplier<String> reader) {
        writer.accept(text);
        // GLFW's setter returns void and can report access-denied only through
        // its error callback (for example on a locked Windows desktop).
        if (!text.equals(reader.get())) throw new IllegalStateException(
                "The system clipboard could not be verified");
    }
{% endif %}
{% if features.workspace_notifications %}

    private boolean tryClipboardWrite(SFMWorkspaceToastQueue.ToastId id, String text,
                                      Consumer<String> writer) {
        try {
            writer.accept(text);
            return true;
        } catch (RuntimeException unavailable) {
            toastQueue().publishPreserving(id, COPY_CONFIRMATION_TOAST_KEY,
                    "Clipboard unavailable; unlock the desktop and try again",
                    SFMWorkspaceToastQueue.Presentation.workspaceStatus(true));
            return false;
        }
    }
{% endif %}
{% if features.workspace_notifications %}

    boolean copyWorkspaceToast(
            SFMWorkspaceToastQueue.ToastId id,
            Consumer<String> clipboardWriter
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(clipboardWriter, "clipboardWriter");
        Optional<SFMWorkspaceToastQueue.Snapshot> source = toastQueue().snapshot(id);
        if (source.isEmpty()) return false;
        SFMWorkspaceToastQueue.Snapshot snapshot = source.orElseThrow();
        if (!tryClipboardWrite(id, snapshot.text(), clipboardWriter)) return false;
        String confirmationKey = COPY_CONFIRMATION_TOAST_KEY.equals(snapshot.replacementKey())
                ? COPY_CONFIRMATION_ALTERNATE_TOAST_KEY
                : COPY_CONFIRMATION_TOAST_KEY;
        toastQueue().publishPreserving(
                id,
                confirmationKey,
                "Copied notification " + id.value() + " to the clipboard",
                SFMWorkspaceToastQueue.Presentation.workspaceStatus(false)
        );
        return true;
    }
{% endif %}
{% if features.workspace_notifications or features.workspace_toast_actions %}

    public SFMWorkspaceToastQueue.MutationResult stopWorkspaceToastTimer(
            SFMWorkspaceToastQueue.ToastId id
    ) {
        return toastQueue().pin(id);
    }
{% endif %}
{% if features.workspace_notifications or features.workspace_toast_actions %}

    public SFMWorkspaceToastQueue.MutationResult resumeWorkspaceToastTimer(
            SFMWorkspaceToastQueue.ToastId id
    ) {
        return toastQueue().resume(id);
    }
{% endif %}
{% if features.workspace_notifications or features.workspace_toast_actions %}

    public SFMWorkspaceToastQueue.MutationResult dismissWorkspaceToast(
            SFMWorkspaceToastQueue.ToastId id
    ) {
        return toastQueue().dismiss(id);
    }
{% endif %}

    @Override
    public boolean isPauseScreen() {
        return true;
    }
{% if features.workspace_panel_entry_controls or features.workspace_notifications %}

    @Override
    public Component getNarrationMessage() {
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
        Component narration = focused == null
                ? Component.literal("Empty SFM workspace")
                : Component.literal("SFM workspace. Focused panel: ").append(focused.narration());
{% if features.workspace_panel_entry_controls %}
        List<SFMWorkspaceLayout.PanelEntry> focusedPane = layout.focusedSlotEntries();
        if (focusedPane.size() > 1) {
            int focusedIndex = 0;
            for (int index = 0; index < focusedPane.size(); index++) {
                if (focusedPane.get(index).id().equals(layout.focusedPanel())) focusedIndex = index;
            }
            narration = narration.copy().append(Component.literal(
                    ". Pane entry " + (focusedIndex + 1) + " of " + focusedPane.size()
                            + ". Numbered controls: left click focuses, middle click closes, right click opens actions."
            ));
        }
{% endif %}
        if (dropFeedback != null) narration = narration.copy().append(Component.literal(". ")).append(dropFeedback);
{% if features.workspace_notifications %}
        Optional<SFMWorkspaceToastQueue.Snapshot> toast = latestWorkspaceToast();
        if (toast.isPresent()) {
            SFMWorkspaceToastQueue.Snapshot snapshot = toast.orElseThrow();
            narration = narration.copy().append(Component.literal(
                    ". Notification " + snapshot.id().value() + ": " + snapshot.text()
                            + ". Left click to copy; right click for actions."
            ));
        }
{% endif %}
        return narration;
    }
{% else %}

    @Override
    public Component getNarrationMessage() {
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
        Component narration = focused == null
                ? Component.literal("Empty SFM workspace")
                : Component.literal("SFM workspace. Focused panel: ").append(focused.narration());
        return dropFeedback == null ? narration : narration.copy().append(Component.literal(". ")).append(dropFeedback);
    }
{% endif %}
{% if features.workspace_widget_hosts %}

    @Override
    @MCVersionDependentBehaviour
    protected void updateNarrationState(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getNarrationMessage());
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
        if (focused != null) {
            focused.widgetHost().ifPresent(host -> host.updateFocusedNarration(output.nest()));
        }
    }
{% endif %}

    @Override
    public void onClose() {
        if (closing) return;
        closing = true;
{% if features.workspace_dividers %}
        disposeDividerInteraction();
{% endif %}
{% if features.workspace_panel_move_gestures %}
        disposePanelMoveInteraction();
{% endif %}
{% if features.workspace_widget_hosts %}
        for (SFMWorkspaceLayout.PanelEntry entry : layout.allPanels()) {
            entry.panel().widgetHost().ifPresent(SFMPanelWidgetHost::closed);
            entry.panel().closed();
        }
{% else %}
        for (SFMWorkspaceLayout.PanelEntry entry : layout.allPanels()) entry.panel().closed();
{% endif %}
        openedPanels.clear();
{% if features.workspace_lifecycle %}
        openedPanelInstances.clear();
{% endif %}
{% if features.workspace_panel_reopening %}
        reopenRecipes.clear();
{% endif %}
{% if features.workspace_notifications or features.workspace_toast_actions %}
        toastQueue().close();
{% endif %}
{% if features.workspace_notifications %}
        workspaceToastHitRegions = List.of();
{% endif %}
{% if features.workspace_panel_entry_controls %}
        panelEntryHitRegions = List.of();
{% endif %}
{% if features.workspace_notifications %}
        capturedWorkspaceToastPointer = null;
{% endif %}
        SFMScreenChangeHelpers.setScreen(previousScreen);
    }
{% if features.workspace_dividers or features.workspace_panel_move_gestures or features.java_symbols %}

    @Override
    public void removed() {
{% if features.workspace_dividers %}
        disposeDividerInteraction();
{% endif %}
{% if features.workspace_panel_move_gestures %}
        disposePanelMoveInteraction();
{% endif %}
{% if features.java_symbols %}
        ca.teamdman.sfm.client.symbol.SFMFindReferencesController.workspaceRemovedProduction(this);
{% endif %}
        super.removed();
    }
{% endif %}
{% if features.workspace_panel_metadata or features.workspace_widget_hosts or features.workspace_panel_entry_controls or features.workspace_panel_move_gestures or features.workspace_dividers or features.client_overlay_scenes or features.workspace_notifications or features.workspace_panel_tooltips or features.workspace_stack_controls %}

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
{% if features.workspace_widget_hosts %}
        synchronizeWidgetHostActivation();
{% endif %}
        this.renderBackground(poseStack);
{% if features.workspace_panel_entry_controls %}
        ArrayList<SFMPanelEntryAffordanceLayout.HitRegion> entryHitRegions = new ArrayList<>();
{% endif %}
{% if features.workspace_panel_metadata or features.workspace_stack_controls or features.workspace_dividers or features.workspace_directional_resize or features.workspace_layout_focus_fix %}
        for (SFMWorkspaceLayout.PanelEntry entry : layout.visiblePanels()) {
{% else %}
        for (SFMWorkspaceLayout.PanelEntry entry : layout.panels()) {
{% endif %}
            SFMScreenPanelBounds bounds = panelBounds.get(entry.id());
            if (bounds == null) continue;
            fill(poseStack, bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), PANEL_BACKGROUND);
            int border = entry.id().equals(layout.focusedPanel()) ? FOCUSED_BORDER : UNFOCUSED_BORDER;
            fill(poseStack, bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + 1, border);
            fill(poseStack, bounds.x(), bounds.y() + bounds.height() - 1, bounds.x() + bounds.width(), bounds.y() + bounds.height(), border);
            fill(poseStack, bounds.x(), bounds.y(), bounds.x() + 1, bounds.y() + bounds.height(), border);
            fill(poseStack, bounds.x() + bounds.width() - 1, bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), border);
{% if features.workspace_panel_metadata or features.workspace_widget_hosts %}
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
{% else %}
            enableScissor(bounds.inset(1));
            entry.panel().render(
                    poseStack,
                    this.minecraft,
                    bounds.inset(1),
                    mouseX,
                    mouseY,
                    partialTick,
                    entry.id().equals(layout.focusedPanel())
            );
            RenderSystem.disableScissor();
{% endif %}
{% if features.workspace_panel_entry_controls %}
            entryHitRegions.addAll(renderEntryAffordances(poseStack, entry, bounds));
{% endif %}
        }
{% if features.workspace_panel_entry_controls %}
        panelEntryHitRegions = List.copyOf(entryHitRegions);
{% endif %}
{% if features.workspace_panel_move_gestures %}
        renderPanelMoveAffordance(poseStack);
{% endif %}
{% if features.workspace_dividers %}
        renderDividerAffordances(poseStack);
{% endif %}
        if (dropFeedback != null) {
            SFMFontUtils.draw(poseStack, this.font, dropFeedback, 6, Math.max(2, this.height - 12), 0xFFFF7777, true);
        }
        super.render(poseStack, mouseX, mouseY, partialTick);
{% if features.client_overlay_scenes %}
        // Forge HUD overlays are painted before Screen content. Repaint the
        // declarative passive scene here so a visible FPS overlay is not
        // hidden behind this full-screen workspace.
        if (!this.minecraft.options.hideGui && this.minecraft.level != null) {
            ca.teamdman.sfm.client.overlay.scene.SFMClientOverlayRuntime.get().renderPassive(
                    poseStack, this.minecraft, this.width, this.height, mouseX, mouseY, partialTick);
        }
{% endif %}
{% if features.workspace_notifications %}
        renderWorkspaceToasts(poseStack, mouseX, mouseY);
{% endif %}
{% if features.workspace_panel_tooltips %}
        renderPanelTooltip(poseStack, mouseX, mouseY);
{% endif %}
{% if features.workspace_panel_entry_controls %}
        renderPanelEntryTooltip(poseStack, mouseX, mouseY);
{% endif %}
{% if features.workspace_notifications %}
        Style toastStyle = workspaceToastStyleAt(mouseX, mouseY);
        if (toastStyle != null) renderComponentHoverEffect(poseStack, toastStyle, mouseX, mouseY);
{% endif %}
{% if features.workspace_dividers %}
        if (dividerInteraction != null) dividerInteraction.reassertCursor();
{% endif %}
    }
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack);
        for (SFMWorkspaceLayout.PanelEntry entry : layout.panels()) {
            SFMScreenPanelBounds bounds = panelBounds.get(entry.id());
            if (bounds == null) continue;
            fill(poseStack, bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), PANEL_BACKGROUND);
            int border = entry.id().equals(layout.focusedPanel()) ? FOCUSED_BORDER : UNFOCUSED_BORDER;
            fill(poseStack, bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + 1, border);
            fill(poseStack, bounds.x(), bounds.y() + bounds.height() - 1, bounds.x() + bounds.width(), bounds.y() + bounds.height(), border);
            fill(poseStack, bounds.x(), bounds.y(), bounds.x() + 1, bounds.y() + bounds.height(), border);
            fill(poseStack, bounds.x() + bounds.width() - 1, bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), border);
            enableScissor(bounds.inset(1));
            entry.panel().render(
                    poseStack,
                    this.minecraft,
                    bounds.inset(1),
                    mouseX,
                    mouseY,
                    partialTick,
                    entry.id().equals(layout.focusedPanel())
            );
            RenderSystem.disableScissor();
        }
        if (dropFeedback != null) {
            SFMFontUtils.draw(poseStack, this.font, dropFeedback, 6, Math.max(2, this.height - 12), 0xFFFF7777, true);
        }
        super.render(poseStack, mouseX, mouseY, partialTick);
    }
{% else %}

    @Override
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
{% case minecraft_version %}
{% when "1.20", "1.20.1" %}
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
{% when "26.1.2" %}
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
{% endcase %}
        for (SFMWorkspaceLayout.PanelEntry entry : layout.panels()) {
            SFMScreenPanelBounds bounds = panelBounds.get(entry.id());
            if (bounds == null) continue;
            graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), PANEL_BACKGROUND);
            int border = entry.id().equals(layout.focusedPanel()) ? FOCUSED_BORDER : UNFOCUSED_BORDER;
            graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + 1, border);
            graphics.fill(bounds.x(), bounds.y() + bounds.height() - 1, bounds.x() + bounds.width(), bounds.y() + bounds.height(), border);
            graphics.fill(bounds.x(), bounds.y(), bounds.x() + 1, bounds.y() + bounds.height(), border);
            graphics.fill(bounds.x() + bounds.width() - 1, bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), border);
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            enableScissor(bounds.inset(1));
{% when "26.1.2" %}
            enableScissor(graphics, bounds.inset(1));
{% endcase %}
            entry.panel().render(
                    graphics,
                    this.minecraft,
                    bounds.inset(1),
                    mouseX,
                    mouseY,
                    partialTick,
                    entry.id().equals(layout.focusedPanel())
            );
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            RenderSystem.disableScissor();
{% when "26.1.2" %}
            graphics.disableScissor();
{% endcase %}
        }
        if (dropFeedback != null) {
            SFMFontUtils.draw(graphics, this.font, dropFeedback, 6, Math.max(2, this.height - 12), 0xFFFF7777, true);
        }
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        super.render(graphics, mouseX, mouseY, partialTick);
{% when "26.1.2" %}
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
{% endcase %}
    }
{% endcase %}
{% endif %}
{% if features.workspace_panel_actions or features.workspace_panel_move_gestures or features.workspace_dividers or features.workspace_widget_hosts %}

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
{% if features.workspace_panel_move_gestures %}
        if (keyCode == GLFW.GLFW_KEY_ESCAPE
                && panelMoveInteraction != null
                && panelMoveInteraction.isCaptured()) {
            panelMoveInteraction.cancel();
            return true;
        }
{% endif %}
{% if features.workspace_dividers %}
        if (keyCode == GLFW.GLFW_KEY_ESCAPE
                && dividerInteraction != null
                && dividerInteraction.isCaptured()) {
            dividerInteraction.cancel();
            return true;
        }
{% endif %}
{% if features.workspace_panel_actions %}
        boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 || Screen.hasControlDown();
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 || Screen.hasShiftDown();
        if (panelGroup != null && control && keyCode == GLFW.GLFW_KEY_M) {
            return invokeWorkspaceAction("sfm:panel/maximize/toggle");
        }
        if (isTabIndexShortcut(keyCode, control, shift,
                (modifiers & (GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) != 0 || Screen.hasAltDown())) {
            int requestedIndex = keyCode - GLFW.GLFW_KEY_1;
            return invokeWorkspaceAction(
                    "sfm:panel/focus/index " + (requestedIndex + 1));
        }
        if (control && keyCode == GLFW.GLFW_KEY_TAB) {
            return invokeWorkspaceAction(
                    shift ? "sfm:panel/focus/previous" : "sfm:panel/focus/next");
        }
{% else %}
        if (panelGroup != null && Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_M) {
            SFMScreenPanel focused = layout.panel(layout.focusedPanel());
            if (focused != null) {
                panelGroup.toggleMaximize(focused);
                refreshLayout(true);
                return true;
            }
        }
        if (Screen.hasControlDown() && keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            int requestedIndex = keyCode - GLFW.GLFW_KEY_1;
            List<SFMWorkspaceLayout.PanelEntry> panels = layout.panels();
            if (requestedIndex < panels.size()) layout.focus(panels.get(requestedIndex).id());
            return requestedIndex < panels.size();
        }
{% endif %}
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
{% if features.workspace_widget_hosts %}
        if (focused != null && focused.widgetHost()
                .map(host -> host.keyPressed(keyCode, scanCode, modifiers)).orElse(false)) return true;
        if (focused != null && !focused.widgetHostOwnsInput()
                && focused.keyPressed(keyCode, scanCode, modifiers)) return true;
{% else %}
        if (focused != null && focused.keyPressed(keyCode, scanCode, modifiers)) return true;
{% endif %}
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
{% if features.workspace_panel_actions %}
{% if features.typed_command_palette %}
            SFMCommandPaletteScreen.openChoices(Component.literal("Close SFM workspace"), escapeChoices());
{% else %}
            onClose();
{% endif %}
{% else %}
            onClose();
{% endif %}
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
{% else %}

    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (panelGroup != null && Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_M) {
{% when "26.1.2" %}
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int keyCode = event.key();
        int scanCode = event.scancode();
        int modifiers = event.modifiers();
        if (panelGroup != null && net.minecraft.client.Minecraft.getInstance().hasControlDown() && keyCode == GLFW.GLFW_KEY_M) {
{% endcase %}
            SFMScreenPanel focused = layout.panel(layout.focusedPanel());
            if (focused != null) {
                panelGroup.toggleMaximize(focused);
                refreshLayout(true);
                return true;
            }
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        if (Screen.hasControlDown() && keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
{% when "26.1.2" %}
        if (net.minecraft.client.Minecraft.getInstance().hasControlDown() && keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
{% endcase %}
            int requestedIndex = keyCode - GLFW.GLFW_KEY_1;
            List<SFMWorkspaceLayout.PanelEntry> panels = layout.panels();
            if (requestedIndex < panels.size()) layout.focus(panels.get(requestedIndex).id());
            return requestedIndex < panels.size();
        }
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
        if (focused != null && focused.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        return super.keyPressed(keyCode, scanCode, modifiers);
{% when "26.1.2" %}
        return super.keyPressed(event);
{% endcase %}
    }
{% endif %}
{% if features.workspace_panel_actions %}

    static boolean isTabIndexShortcut(int keyCode, boolean control, boolean shift, boolean otherModifier) {
        // Modified digit chords belong to panel content (for example editor Fit Width).
        return control && !shift && !otherModifier
                && keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9;
    }
{% endif %}
{% if features.workspace_panel_actions or features.workspace_toast_actions %}

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
{% endif %}
{% if features.workspace_panel_actions %}

    static List<SFMActionChoice> diagnosticChoices() {
        return diagnosticChoices(null);
    }
{% endif %}
{% if features.workspace_panel_actions %}

    public static List<SFMActionChoice> diagnosticChoices(@Nullable SFMScreenPanel focused) {
        String scene = "sfm:size_display";
        List<SFMActionChoice> choices = new ArrayList<>();
{% if features.terminal_properties %}
        if (focused instanceof SFMTerminalPanel terminal && terminal.isRustBacked()) {
            choices.add(SFMActionChoice.invoke(OPEN_PANEL_RIGHT, "sfm:terminal_properties"));
        } else if (focused instanceof SFMTerminalPropertiesPanel) {
            choices.add(SFMActionChoice.invoke(CLOSE_PANEL, ""));
        }
{% endif %}
{% if features.input_diagnostics_panel %}
        if (focused instanceof SFMInputDiagnosticsPanel) {
            choices.add(SFMActionChoice.invoke(CLOSE_PANEL, ""));
        } else {
            choices.add(SFMActionChoice.invoke(OPEN_PANEL, "sfm:input_diagnostics"));
            choices.add(SFMActionChoice.invoke(OPEN_PANEL_LEFT, "sfm:input_diagnostics"));
            choices.add(SFMActionChoice.invoke(OPEN_PANEL_RIGHT, "sfm:input_diagnostics"));
            choices.add(SFMActionChoice.invoke(OPEN_PANEL_ABOVE, "sfm:input_diagnostics"));
            choices.add(SFMActionChoice.invoke(OPEN_PANEL_BELOW, "sfm:input_diagnostics"));
        }
{% endif %}
{% if features.client_overlay_scenes %}
        choices.add(SFMActionChoice.invoke(
                TOGGLE_OVERLAY_VISIBILITY,
                FPS_OVERLAY_SELECTOR,
                fpsOverlayToggleLabel()
        ));
{% endif %}
        choices.add(SFMActionChoice.invoke(OPEN_PANEL, scene));
        choices.add(SFMActionChoice.invoke(OPEN_PANEL_LEFT, scene));
        choices.add(SFMActionChoice.invoke(OPEN_PANEL_RIGHT, scene));
        choices.add(SFMActionChoice.invoke(OPEN_PANEL_ABOVE, scene));
        choices.add(SFMActionChoice.invoke(OPEN_PANEL_BELOW, scene));
        return List.copyOf(choices);
    }
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.client_overlay_scenes %}

    private static String fpsOverlayToggleLabel() {
        return Minecraft.getInstance() == null
                ? TOGGLE_FPS_OVERLAY.getStub()
                : TOGGLE_FPS_OVERLAY.getString();
    }
{% endif %}
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.command_palette %}

    static List<SFMActionChoice> escapeChoices() {
        return List.of(
                SFMActionChoice.invoke(CLOSE_PANEL, ""),
                SFMActionChoice.invoke(CLOSE_SCREEN, ""),
                SFMActionChoice.invoke(CLOSE_PALETTE, "")
        );
    }
{% endif %}
{% endif %}
{% if features.workspace_widget_hosts %}

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
        return focused != null && (focused.widgetHost()
                .map(host -> host.keyReleased(keyCode, scanCode, modifiers)).orElse(false)
                || !focused.widgetHostOwnsInput() && focused.keyReleased(keyCode, scanCode, modifiers)
                || super.keyReleased(keyCode, scanCode, modifiers));
    }
{% else %}

    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
{% when "26.1.2" %}
    public boolean keyReleased(net.minecraft.client.input.KeyEvent event) {
        int keyCode = event.key();
        int scanCode = event.scancode();
        int modifiers = event.modifiers();
{% endcase %}
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
        return focused != null && (focused.keyReleased(keyCode, scanCode, modifiers)
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                || super.keyReleased(keyCode, scanCode, modifiers));
{% when "26.1.2" %}
                || super.keyReleased(event));
{% endcase %}
    }
{% endif %}
{% if features.workspace_widget_hosts %}

    @Override
    public boolean charTyped(char character, int modifiers) {
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
        return focused != null && (focused.widgetHost()
                .map(host -> host.charTyped(character, modifiers)).orElse(false)
                || !focused.widgetHostOwnsInput() && focused.charTyped(character, modifiers)
                || super.charTyped(character, modifiers));
    }
{% else %}

    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    public boolean charTyped(char character, int modifiers) {
{% when "26.1.2" %}
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        char character = (char) event.codepoint();
        int modifiers = 0;
{% endcase %}
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
        return focused != null && (focused.charTyped(character, modifiers)
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                || super.charTyped(character, modifiers));
{% when "26.1.2" %}
                || super.charTyped(event));
{% endcase %}
    }
{% endif %}
{% if features.workspace_notifications or features.workspace_panel_entry_controls or features.workspace_dividers or features.workspace_panel_move_gestures or features.workspace_focus_tracking or features.workspace_keyboard_context or features.context_actions or features.workspace_panel_metadata or features.workspace_widget_hosts %}

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
{% if features.workspace_notifications %}
        Optional<SFMWorkspaceToastQueue.ToastId> toast = workspaceToastAt(mouseX, mouseY);
        if (toast.isPresent()) {
            SFMWorkspaceToastQueue.ToastId id = toast.orElseThrow();
            capturedWorkspaceToastPointer = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? null : id;
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
{% if features.workspace_toast_path_actions %}
                Optional<SFMPath> path = SFMWorkspaceToastContent.pathInStyle(workspaceToastStyleAt(mouseX, mouseY));
                int index = path.map(workspaceToastPaths(id)::indexOf).orElse(-1);
                invokeWorkspaceAction(index < 0 ? workspaceToastActionDraft(COPY_TOAST, id)
                        : SFMToastPathAction.Operation.COPY.id() + " " + id.commandArgument() + " " + index);
{% else %}
{% if features.workspace_toast_actions %}
                invokeWorkspaceAction(workspaceToastActionDraft(COPY_TOAST, id));
{% else %}
                copyWorkspaceToast(id);
{% endif %}
{% endif %}
            }
{% if features.workspace_toast_actions %}
{% if features.typed_command_palette %}
            else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) openWorkspaceToastActions(id);
{% else %}
            else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) capturedWorkspaceToastPointer = id;
{% endif %}
{% else %}
            else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) capturedWorkspaceToastPointer = id;
{% endif %}
            return true;
        }
{% endif %}
{% if features.workspace_panel_entry_controls %}
        Optional<SFMPanelEntryAffordanceLayout.HitRegion> panelEntry = panelEntryAt(mouseX, mouseY);
        if (panelEntry.isPresent()) {
            SFMPanelEntryAffordanceLayout.HitRegion hit = panelEntry.orElseThrow();
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                invokePanelEntryAction(hit, SFMWorkspaceLifecycleActionIds.PANEL_ENTRY_FOCUS);
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
{% if features.workspace_panel_move_gestures %}
                SFMWorkspacePanelMoveInteraction.Target target = panelMoveTarget(hit);
                boolean captured = target != null
                        && panelMoveInteraction != null
                        && panelMoveInteraction.pointerPressedFromAffordance(
                                target, mouseX, mouseY, button);
                if (!captured && (panelMoveInteraction == null || !panelMoveInteraction.isCaptured())) {
                    // Preserve the old headless/first-frame fallback when the move
                    // interaction has not been initialized or cannot capture.
                    invokePanelEntryAction(hit, SFMWorkspaceLifecycleActionIds.PANEL_ENTRY_CLOSE);
                }
{% else %}
                invokePanelEntryAction(hit, SFMWorkspaceLifecycleActionIds.PANEL_ENTRY_CLOSE);
{% endif %}
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                openPanelEntryActions(hit);
            }
            return true;
        }
{% endif %}
{% if features.workspace_dividers %}
        if (dividerInteraction != null
                && dividerInteraction.pointerPressed(mouseX, mouseY, button)) return true;
{% endif %}
{% if features.workspace_panel_move_gestures %}
        if (button == SFMWorkspacePanelMoveInteraction.BUTTON
                && panelMoveInteraction != null
                && panelMoveInteraction.pointerPressedFromContent(
                        mouseX, mouseY, button,
                        ca.teamdman.sfm.client.input.SFMPointerInputModifiers.isDown(
                                GLFW.GLFW_MOD_ALT, Screen::hasAltDown))) return true;
{% endif %}
        SFMWorkspaceLayout.PanelEntry entry = panelAt(mouseX, mouseY);
        if (entry != null) {
{% if features.workspace_focus_tracking or features.workspace_keyboard_context or features.context_actions %}
            observeWorkspaceFocus();
{% endif %}
            layout.focus(entry.id());
{% if features.workspace_focus_tracking or features.workspace_keyboard_context or features.context_actions %}
            observeWorkspaceFocus();
{% endif %}
{% if features.workspace_widget_hosts %}
            synchronizeWidgetHostActivation();
{% endif %}
{% if features.workspace_panel_metadata or features.workspace_widget_hosts %}
            int[] local = localMouse(entry, mouseX, mouseY);
{% if features.workspace_widget_hosts %}
            boolean childHandled = entry.panel().widgetHost()
                    .map(host -> host.mouseClicked(local[0], local[1], button)).orElse(false);
            if (!childHandled && !entry.panel().widgetHostOwnsInput()) {
                entry.panel().mouseClicked(local[0], local[1], button);
            }
{% else %}
            entry.panel().mouseClicked(local[0], local[1], button);
{% endif %}
{% else %}
            entry.panel().mouseClicked(mouseX, mouseY, button);
{% endif %}
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
{% else %}

    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
{% when "26.1.2" %}
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
{% endcase %}
        SFMWorkspaceLayout.PanelEntry entry = panelAt(mouseX, mouseY);
        if (entry != null) {
            layout.focus(entry.id());
            entry.panel().mouseClicked(mouseX, mouseY, button);
            return true;
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        return super.mouseClicked(mouseX, mouseY, button);
{% when "26.1.2" %}
        return super.mouseClicked(event, doubleClick);
{% endcase %}
    }
{% endif %}
{% if features.workspace_notifications or features.workspace_dividers or features.workspace_panel_metadata or features.workspace_widget_hosts %}

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
{% if features.workspace_notifications %}
        Optional<SFMWorkspaceToastQueue.ToastId> toast = workspaceToastAt(mouseX, mouseY);
        toastQueue().setHovered(toast.orElse(null));
        if (toast.isPresent()) {
            super.mouseMoved(mouseX, mouseY);
            return;
        }
{% endif %}
{% if features.workspace_dividers %}
        if (dividerInteraction != null) {
            dividerInteraction.pointerMoved(mouseX, mouseY);
            if (dividerInteraction.isHoveringDivider() || dividerInteraction.isCaptured()) {
                super.mouseMoved(mouseX, mouseY);
                return;
            }
        }
{% endif %}
        SFMWorkspaceLayout.PanelEntry entry = panelAt(mouseX, mouseY);
{% if features.workspace_panel_metadata or features.workspace_widget_hosts %}
        if (entry != null) {
            int[] local = localMouse(entry, mouseX, mouseY);
{% if features.workspace_widget_hosts %}
            entry.panel().widgetHost().ifPresent(host -> host.mouseMoved(local[0], local[1]));
            if (!entry.panel().widgetHostOwnsInput()) entry.panel().mouseMoved(local[0], local[1]);
{% else %}
            entry.panel().mouseMoved(local[0], local[1]);
{% endif %}
        }
{% else %}
        if (entry != null) entry.panel().mouseMoved(mouseX, mouseY);
{% endif %}
        super.mouseMoved(mouseX, mouseY);
    }
{% else %}

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        SFMWorkspaceLayout.PanelEntry entry = panelAt(mouseX, mouseY);
        if (entry != null) entry.panel().mouseMoved(mouseX, mouseY);
        super.mouseMoved(mouseX, mouseY);
    }
{% endif %}
{% if features.workspace_panel_move_gestures or features.workspace_notifications or features.workspace_dividers or features.workspace_panel_metadata or features.workspace_widget_hosts %}

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
{% if features.workspace_panel_move_gestures %}
        if (panelMoveInteraction != null
                && panelMoveInteraction.isCaptured()
                && panelMoveInteraction.pointerReleased(mouseX, mouseY, button)) return true;
{% endif %}
{% if features.workspace_notifications %}
        if (capturedWorkspaceToastPointer != null) {
            capturedWorkspaceToastPointer = null;
            return true;
        }
        if (workspaceToastAt(mouseX, mouseY).isPresent()) return true;
{% endif %}
{% if features.workspace_dividers %}
        if (dividerInteraction != null
                && dividerInteraction.pointerReleased(mouseX, mouseY, button)) return true;
{% endif %}
{% if features.workspace_panel_move_gestures %}
        if (panelMoveInteraction != null
                && panelMoveInteraction.pointerReleased(mouseX, mouseY, button)) return true;
{% endif %}
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
{% if features.workspace_panel_metadata or features.workspace_widget_hosts %}
{% if features.workspace_panel_metadata %}
        SFMWorkspaceLayout.PanelEntry entry = focused == null ? null : layout.entry(layout.focusedPanel());
{% else %}
        SFMWorkspaceLayout.PanelEntry entry = focused == null ? null : layout.panels().stream()
                .filter(candidate -> candidate.id().equals(layout.focusedPanel())).findFirst().orElse(null);
{% endif %}
        int[] local = entry == null ? new int[]{0, 0} : localMouse(entry, mouseX, mouseY);
{% if features.workspace_widget_hosts %}
        return focused != null && (focused.widgetHost()
                .map(host -> host.mouseReleased(local[0], local[1], button)).orElse(false)
                || !focused.widgetHostOwnsInput() && focused.mouseReleased(local[0], local[1], button)
                || super.mouseReleased(mouseX, mouseY, button));
{% else %}
        return focused != null && (focused.mouseReleased(local[0], local[1], button)
                || super.mouseReleased(mouseX, mouseY, button));
{% endif %}
{% else %}
        return focused != null && (focused.mouseReleased(mouseX, mouseY, button)
                || super.mouseReleased(mouseX, mouseY, button));
{% endif %}
    }
{% else %}

    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
{% when "26.1.2" %}
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
{% endcase %}
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
        return focused != null && (focused.mouseReleased(mouseX, mouseY, button)
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                || super.mouseReleased(mouseX, mouseY, button));
{% when "26.1.2" %}
                || super.mouseReleased(event));
{% endcase %}
    }
{% endif %}
{% if features.workspace_panel_move_gestures or features.workspace_notifications or features.workspace_dividers or features.workspace_panel_metadata or features.workspace_widget_hosts %}

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
{% if features.workspace_panel_move_gestures %}
        if (panelMoveInteraction != null
                && panelMoveInteraction.isCaptured()
                && panelMoveInteraction.pointerDragged(mouseX, mouseY, button)) return true;
{% endif %}
{% if features.workspace_notifications %}
        if (capturedWorkspaceToastPointer != null) return true;
        if (workspaceToastAt(mouseX, mouseY).isPresent()) return true;
{% endif %}
{% if features.workspace_dividers %}
        if (dividerInteraction != null
                && dividerInteraction.pointerDragged(mouseX, mouseY, button)) return true;
{% endif %}
{% if features.workspace_panel_move_gestures %}
        if (panelMoveInteraction != null
                && panelMoveInteraction.pointerDragged(mouseX, mouseY, button)) return true;
{% endif %}
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
{% if features.workspace_panel_metadata or features.workspace_widget_hosts %}
{% if features.workspace_panel_metadata %}
        SFMWorkspaceLayout.PanelEntry entry = focused == null ? null : layout.entry(layout.focusedPanel());
{% else %}
        SFMWorkspaceLayout.PanelEntry entry = focused == null ? null : layout.panels().stream()
                .filter(candidate -> candidate.id().equals(layout.focusedPanel())).findFirst().orElse(null);
{% endif %}
        int[] local = entry == null ? new int[]{0, 0} : localMouse(entry, mouseX, mouseY);
        double scale = entry == null ? 1.0D : panelRenderScale(entry);
        double localDragX = dragX / scale;
        double localDragY = dragY / scale;
{% if features.workspace_widget_hosts %}
        return focused != null && (focused.widgetHost()
                .map(host -> host.mouseDragged(local[0], local[1], button, localDragX, localDragY)).orElse(false)
                || !focused.widgetHostOwnsInput()
                && focused.mouseDragged(local[0], local[1], button, localDragX, localDragY)
                || super.mouseDragged(mouseX, mouseY, button, dragX, dragY));
{% else %}
        return focused != null && (focused.mouseDragged(local[0], local[1], button, localDragX, localDragY)
                || super.mouseDragged(mouseX, mouseY, button, dragX, dragY));
{% endif %}
{% else %}
        return focused != null && (focused.mouseDragged(mouseX, mouseY, button, dragX, dragY)
                || super.mouseDragged(mouseX, mouseY, button, dragX, dragY));
{% endif %}
    }
{% else %}

    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
{% when "26.1.2" %}
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
{% endcase %}
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
        return focused != null && (focused.mouseDragged(mouseX, mouseY, button, dragX, dragY)
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                || super.mouseDragged(mouseX, mouseY, button, dragX, dragY));
{% when "26.1.2" %}
                || super.mouseDragged(event, dragX, dragY));
{% endcase %}
    }
{% endif %}
{% if features.workspace_notifications or features.workspace_focus_tracking or features.workspace_keyboard_context or features.context_actions or features.workspace_panel_metadata or features.workspace_widget_hosts %}

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
{% if features.workspace_notifications %}
        if (workspaceToastAt(mouseX, mouseY).isPresent()) return true;
{% endif %}
        SFMWorkspaceLayout.PanelEntry entry = panelAt(mouseX, mouseY);
        if (entry != null) {
{% if features.workspace_focus_tracking or features.workspace_keyboard_context or features.context_actions %}
            observeWorkspaceFocus();
{% endif %}
            layout.focus(entry.id());
{% if features.workspace_focus_tracking or features.workspace_keyboard_context or features.context_actions %}
            observeWorkspaceFocus();
{% endif %}
{% if features.workspace_widget_hosts %}
            synchronizeWidgetHostActivation();
{% endif %}
        }
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
{% if features.workspace_panel_metadata or features.workspace_widget_hosts %}
{% if features.workspace_panel_metadata %}
        SFMWorkspaceLayout.PanelEntry focusedEntry = focused == null ? null : layout.entry(layout.focusedPanel());
{% else %}
        SFMWorkspaceLayout.PanelEntry focusedEntry = focused == null ? null : layout.panels().stream()
                .filter(candidate -> candidate.id().equals(layout.focusedPanel())).findFirst().orElse(null);
{% endif %}
        int[] local = focusedEntry == null ? new int[]{0, 0} : localMouse(focusedEntry, mouseX, mouseY);
{% if features.workspace_widget_hosts %}
        return focused != null && (focused.widgetHost()
                .map(host -> host.mouseScrolled(local[0], local[1], delta)).orElse(false)
                || !focused.widgetHostOwnsInput() && focused.mouseScrolled(local[0], local[1], delta)
                || super.mouseScrolled(mouseX, mouseY, delta));
{% else %}
        return focused != null && (focused.mouseScrolled(local[0], local[1], delta)
                || super.mouseScrolled(mouseX, mouseY, delta));
{% endif %}
{% else %}
        return focused != null && (focused.mouseScrolled(mouseX, mouseY, delta)
                || super.mouseScrolled(mouseX, mouseY, delta));
{% endif %}
    }
{% else %}

    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalDelta, double delta) {
{% endcase %}
        SFMWorkspaceLayout.PanelEntry entry = panelAt(mouseX, mouseY);
        if (entry != null) layout.focus(entry.id());
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
        return focused != null && (focused.mouseScrolled(mouseX, mouseY, delta)
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
                || super.mouseScrolled(mouseX, mouseY, delta));
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
                || super.mouseScrolled(mouseX, mouseY, horizontalDelta, delta));
{% endcase %}
    }
{% endif %}

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
{% if features.workspace_panel_metadata or features.workspace_stack_controls or features.workspace_dividers or features.workspace_directional_resize or features.workspace_layout_focus_fix %}

    private @Nullable SFMWorkspaceLayout.PanelEntry panelAt(double mouseX, double mouseY) {
        for (SFMWorkspaceLayout.PanelEntry entry : layout.visiblePanels()) {
            SFMScreenPanelBounds bounds = panelBounds.get(entry.id());
            if (bounds != null && bounds.contains(mouseX, mouseY)) return entry;
        }
        return null;
    }
{% else %}

    private @Nullable SFMWorkspaceLayout.PanelEntry panelAt(double mouseX, double mouseY) {
        for (SFMWorkspaceLayout.PanelEntry entry : layout.panels()) {
            SFMScreenPanelBounds bounds = panelBounds.get(entry.id());
            if (bounds != null && bounds.contains(mouseX, mouseY)) return entry;
        }
        return null;
    }
{% endif %}
{% if features.workspace_panel_move_gestures %}

    private SFMWorkspacePanelMoveInteraction createPanelMoveInteraction() {
        return new SFMWorkspacePanelMoveInteraction(new SFMWorkspacePanelMoveInteraction.Host() {
            @Override
            public @Nullable SFMWorkspacePanelMoveInteraction.Target targetAt(
                    double mouseX,
                    double mouseY
            ) {
                SFMWorkspaceLayout.PanelEntry entry = panelAt(mouseX, mouseY);
                if (entry == null) return null;
                SFMScreenPanelBounds bounds = panelBounds.get(entry.id());
                return bounds == null ? null : new SFMWorkspacePanelMoveInteraction.Target(
                        entry.id(), entry.panel(), bounds);
            }

            @Override
            public void openActions(SFMWorkspacePanelMoveInteraction.Target source) {
                exactPanelEntryHit(source).ifPresent(SFMScreenMultiplexer.this::openPanelEntryActions);
            }

            @Override
            public boolean moveToStack(
                    SFMWorkspacePanelMoveInteraction.Target source,
                    SFMWorkspacePanelMoveInteraction.Target destination
            ) {
                return invokePanelEntryMoveTo(source, destination);
            }
        });
    }
{% endif %}
{% if features.workspace_panel_move_gestures %}

    private @Nullable SFMWorkspacePanelMoveInteraction.Target panelMoveTarget(
            SFMPanelEntryAffordanceLayout.HitRegion hit
    ) {
        if (layout.panel(hit.entryId()) != hit.capturedPanel()) return null;
        SFMScreenPanelBounds bounds = panelBounds.get(hit.entryId());
        return bounds == null ? null : new SFMWorkspacePanelMoveInteraction.Target(
                hit.entryId(), hit.capturedPanel(), bounds);
    }
{% endif %}
{% if features.workspace_panel_move_gestures %}

    private Optional<SFMPanelEntryAffordanceLayout.HitRegion> exactPanelEntryHit(
            SFMWorkspacePanelMoveInteraction.Target target
    ) {
        if (layout.panel(target.entryId()) != target.panel()) return Optional.empty();
        Optional<SFMWorkspaceStackId> paneId = layout.stackId(target.entryId());
        if (paneId.isEmpty()) return Optional.empty();
        List<SFMWorkspaceLayout.PanelEntry> entries = layout.slotEntries(target.entryId());
        for (int index = 0; index < entries.size(); index++) {
            SFMWorkspaceLayout.PanelEntry entry = entries.get(index);
            if (!entry.id().equals(target.entryId()) || entry.panel() != target.panel()) continue;
            return Optional.of(new SFMPanelEntryAffordanceLayout.HitRegion(
                    paneId.orElseThrow(),
                    entry.id(),
                    entry.panel(),
                    index,
                    entries.size(),
                    target.bounds(),
                    entry.id().equals(layout.focusedPanel())
            ));
        }
        return Optional.empty();
    }
{% endif %}
{% if features.workspace_panel_move_gestures %}

    private boolean invokePanelEntryMoveTo(
            SFMWorkspacePanelMoveInteraction.Target source,
            SFMWorkspacePanelMoveInteraction.Target destination
    ) {
        Optional<SFMPanelEntryAffordanceLayout.HitRegion> sourceHit = exactPanelEntryHit(source);
        Optional<SFMPanelEntryAffordanceLayout.HitRegion> destinationHit = exactPanelEntryHit(destination);
        if (sourceHit.isEmpty() || destinationHit.isEmpty()) return false;
        Optional<SFMPanelEntryInteractionSessionService.Session> sourceSession =
                SFMPanelEntryInteractionSessionService.create(this, sourceHit.orElseThrow());
        Optional<SFMPanelEntryInteractionSessionService.Session> destinationSession =
                SFMPanelEntryInteractionSessionService.create(this, destinationHit.orElseThrow());
        if (sourceSession.isEmpty() || destinationSession.isEmpty()) {
            sourceSession.ifPresent(SFMPanelEntryInteractionSessionService::invalidate);
            destinationSession.ifPresent(SFMPanelEntryInteractionSessionService::invalidate);
            return false;
        }
        SFMPanelEntryInteractionSessionService.Session capturedSource = sourceSession.orElseThrow();
        SFMPanelEntryInteractionSessionService.Session capturedDestination = destinationSession.orElseThrow();
        try {
            return invokeWorkspaceAction(
                    SFMWorkspaceLifecycleActionIds.PANEL_ENTRY_MOVE_TO + " "
                            + capturedSource.commandArgument() + " "
                            + capturedDestination.commandArgument());
        } finally {
            SFMPanelEntryInteractionSessionService.invalidate(capturedSource);
            SFMPanelEntryInteractionSessionService.invalidate(capturedDestination);
        }
    }
{% endif %}
{% if features.workspace_panel_move_gestures %}

    private void renderPanelMoveAffordance(PoseStack poseStack) {
        SFMWorkspacePanelMoveInteraction interaction = panelMoveInteraction;
        if (interaction == null) return;
        interaction.snapshot().destination().ifPresent(target -> {
            if (layout.panel(target.entryId()) != target.panel()) return;
            SFMScreenPanelBounds bounds = panelBounds.get(target.entryId());
            if (bounds == null) return;
            fill(poseStack, bounds.x(), bounds.y(),
                    bounds.x() + bounds.width(), bounds.y() + bounds.height(), 0x4055FFFF);
            fill(poseStack, bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + 2, FOCUSED_BORDER);
            fill(poseStack, bounds.x(), bounds.y() + bounds.height() - 2,
                    bounds.x() + bounds.width(), bounds.y() + bounds.height(), FOCUSED_BORDER);
            fill(poseStack, bounds.x(), bounds.y(), bounds.x() + 2, bounds.y() + bounds.height(), FOCUSED_BORDER);
            fill(poseStack, bounds.x() + bounds.width() - 2, bounds.y(),
                    bounds.x() + bounds.width(), bounds.y() + bounds.height(), FOCUSED_BORDER);
        });
    }
{% endif %}
{% if features.workspace_panel_metadata or features.workspace_widget_hosts or features.workspace_panel_measurement %}

    private SFMScreenPanelBounds contentBounds(SFMWorkspaceLayout.PanelEntry entry) {
        SFMScreenPanelBounds bounds = panelBounds.get(entry.id());
        if (bounds == null) return new SFMScreenPanelBounds(0, 0, 0, 0);
        SFMScreenPanelBounds physical = bounds.inset(1);
{% if features.workspace_panel_metadata %}
        double scale = panelRenderScale(entry);
{% else %}
        double scale = 1.0D;
{% endif %}
        return new SFMScreenPanelBounds(
                0,
                0,
                Math.max(1, (int) Math.ceil(physical.width() / scale)),
                Math.max(1, (int) Math.ceil(physical.height() / scale))
        );
    }
{% endif %}
{% if features.workspace_panel_metadata or features.workspace_widget_hosts %}

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
{% if features.workspace_widget_hosts %}
        entry.panel().widgetHost().ifPresent(host -> host.render(poseStack, local[0], local[1], partialTick));
{% endif %}
        poseStack.popPose();
    }
{% endif %}
{% if features.workspace_panel_metadata or features.workspace_widget_hosts or features.workspace_panel_measurement %}

    private double panelRenderScale(SFMWorkspaceLayout.PanelEntry entry) {
{% if features.workspace_panel_metadata %}
        Integer override = entry.metadata().guiScaleOverride();
        if (override == null || this.minecraft == null) return 1.0D;
        return override / Math.max(1.0D, this.minecraft.getWindow().getGuiScale());
{% else %}
        return 1.0D;
{% endif %}
    }
{% endif %}
{% if features.workspace_panel_metadata or features.workspace_widget_hosts %}

    private int[] localMouse(SFMWorkspaceLayout.PanelEntry entry, double mouseX, double mouseY) {
        SFMScreenPanelBounds physical = panelBounds.get(entry.id());
        if (physical == null) return new int[]{0, 0};
        SFMScreenPanelBounds content = physical.inset(1);
        double scale = panelRenderScale(entry);
{% if features.workspace_widget_hosts %}
        return SFMPanelWidgetHost.panelCoordinates(content, scale, mouseX, mouseY);
{% else %}
        return new int[]{(int) Math.floor((mouseX - content.x()) / scale),
                (int) Math.floor((mouseY - content.y()) / scale)};
{% endif %}
    }
{% endif %}
{% if features.workspace_widget_hosts %}

    private void synchronizeWidgetHostActivation() {
        SFMWorkspacePanelId focusedPanel = layout.focusedPanel();
        for (SFMWorkspaceLayout.PanelEntry entry : layout.allPanels()) {
            entry.panel().widgetHost().ifPresent(host -> host.setActive(entry.id().equals(focusedPanel)));
        }
    }
{% endif %}
{% if features.workspace_keyboard_context %}

    /** Captures the exact panel/widget context targeted by one key event. */
    public SFMKeyboardUsageContextSnapshot keyboardUsageContextSnapshot() {
        observeWorkspaceFocus();
        SFMWorkspacePanelId panelId = layout.focusedPanel();
        SFMScreenPanel panel = layout.panel(panelId);
{% if features.workspace_widget_hosts %}
        @Nullable SFMPanelWidgetHost host = panel == null
                ? null
                : panel.widgetHost().orElse(null);
        @Nullable SFMPanelWidget child = host == null
                ? null
                : host.focusedChild().orElse(null);
{% endif %}
{% if features.workspace_widget_hosts %}
        ResourceLocation deepest = child == null
                ? (panel == null ? SFMKeyboardUsageSituations.DEFAULT : panel.keyboardUsageSituationId())
                : child.keyboardUsageSituationId();
{% else %}
        ResourceLocation deepest = panel == null ? SFMKeyboardUsageSituations.DEFAULT : panel.keyboardUsageSituationId();
{% endif %}
        SFMKeyboardUsageSituationCatalog.ActiveAncestry ancestry =
                SFMKeyboardUsageSituations.catalog().resolve(deepest);
        List<ResourceLocation> situations = ancestry.situations();
{% if features.workspace_widget_hosts %}
        @Nullable ResourceLocation elementId = child == null ? null : child.elementId();
{% else %}
        @Nullable ResourceLocation elementId = null;
{% endif %}
        long capturedWorkspaceRevision = keyboardFocusRevision;
{% if features.workspace_widget_hosts %}
        long capturedElementRevision = host == null ? 0 : host.focusRevision();
{% else %}
        long capturedElementRevision = 0;
{% endif %}
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
{% endif %}
{% if features.workspace_keyboard_context %}

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
{% if features.workspace_widget_hosts %}
        @Nullable SFMPanelWidgetHost host = panel.widgetHost().orElse(null);
        if (host == null) return elementId == null && elementRevision == 0;
        return host.focusRevision() == elementRevision
                && java.util.Objects.equals(host.focusedElementId().orElse(null), elementId);
{% else %}
        return elementId == null && elementRevision == 0;
{% endif %}
    }
{% endif %}
{% if features.workspace_focus_tracking or features.workspace_keyboard_context or features.context_actions %}

    private void observeWorkspaceFocus() {
        SFMWorkspacePanelId focused = layout.focusedPanel();
        if (java.util.Objects.equals(observedFocusedPanel, focused)) return;
        observedFocusedPanel = focused;
{% if features.workspace_focus_tracking %}
        SFMScreenPanel focusedPanel = focused == null ? null : layout.panel(focused);
        if (focusedPanel != null) {
            ArrayDeque<FocusedPanelWitness> history = focusHistory();
            history.removeIf(witness -> witness.id().equals(focused) || witness.panel() == focusedPanel);
            history.addFirst(new FocusedPanelWitness(focused, focusedPanel));
            while (history.size() > MAX_FOCUS_HISTORY_ENTRIES) history.removeLast();
        }
{% endif %}
        keyboardFocusRevision++;
{% if features.workspace_keyboard_context %}
        // Pure layout/action tests construct a workspace without bootstrapping
        // Minecraft. Revision identity still updates there; only the live
        // input engine needs an eager reset.
        if (Minecraft.getInstance() != null) {
            SFMKeyBindingService.INSTANCE.reset(SFMKeyBindingEngine.ResetReason.CONTEXT_CHANGED);
        }
{% endif %}
    }
{% endif %}
{% if features.workspace_focus_tracking %}

    private ArrayDeque<FocusedPanelWitness> focusHistory() {
        if (focusHistory == null) focusHistory = new ArrayDeque<>();
        return focusHistory;
    }
{% endif %}
{% if features.workspace_focus_tracking %}

    private record FocusedPanelWitness(SFMWorkspacePanelId id, SFMScreenPanel panel) {
    }
{% endif %}
{% if features.workspace_panel_entry_controls %}


    private List<SFMPanelEntryAffordanceLayout.HitRegion> renderEntryAffordances(
            PoseStack poseStack,
            SFMWorkspaceLayout.PanelEntry entry,
            SFMScreenPanelBounds bounds
    ) {
        List<SFMWorkspaceLayout.PanelEntry> slot = layout.slotEntries(entry.id());
        Optional<SFMWorkspaceStackId> paneId = layout.stackId(entry.id());
        if (paneId.isEmpty()) return List.of();
        List<SFMPanelEntryAffordanceLayout.HitRegion> regions =
                SFMPanelEntryAffordanceLayout.layout(
                        bounds,
                        paneId.orElseThrow(),
                        slot,
                        layout.focusedPanel()
                );
        for (SFMPanelEntryAffordanceLayout.HitRegion region : regions) {
            SFMScreenPanelBounds hit = region.bounds();
            int background = region.focused() ? 0xFF55FFFF : 0xCC303030;
            int foreground = region.focused() ? 0xFF101010 : 0xFFFFFFFF;
            fill(poseStack, hit.x(), hit.y(), hit.x() + hit.width(), hit.y() + hit.height(), background);
            String label = Integer.toString(region.oneBasedIndex());
            SFMFontUtils.draw(poseStack, this.font, label,
                    hit.x() + (hit.width() - this.font.width(label)) / 2,
                    hit.y() + 2,
                    foreground,
                    true);
        }
        return regions;
    }
{% endif %}
{% if features.workspace_panel_entry_controls %}

    private Optional<SFMPanelEntryAffordanceLayout.HitRegion> panelEntryAt(
            double mouseX,
            double mouseY
    ) {
        // Headless and first-frame input can arrive before numbered-entry geometry has been painted.
        // An absent cache means there is no entry affordance to hit; it must not prevent divider or
        // panel input from continuing through the ordinary routing path.
        List<SFMPanelEntryAffordanceLayout.HitRegion> regions = panelEntryHitRegions;
        if (regions == null || regions.isEmpty()) return Optional.empty();
        return SFMPanelEntryAffordanceLayout.hitTest(regions, mouseX, mouseY)
                .filter(hit -> layout.panel(hit.entryId()) == hit.capturedPanel())
                .filter(hit -> layout.stackId(hit.entryId()).filter(hit.paneId()::equals).isPresent());
    }
{% endif %}
{% if features.workspace_panel_entry_controls %}

    private boolean invokePanelEntryAction(
            SFMPanelEntryAffordanceLayout.HitRegion hit,
            ResourceLocation actionId
    ) {
        Optional<SFMPanelEntryInteractionSessionService.Session> captured =
                SFMPanelEntryInteractionSessionService.create(this, hit);
        if (captured.isEmpty()) return false;
        SFMPanelEntryInteractionSessionService.Session session = captured.orElseThrow();
        try {
            return invokeWorkspaceAction(actionId + " " + session.commandArgument());
        } finally {
            SFMPanelEntryInteractionSessionService.invalidate(session);
        }
    }
{% endif %}
{% if features.workspace_panel_entry_controls %}

    private boolean openPanelEntryActions(SFMPanelEntryAffordanceLayout.HitRegion hit) {
{% if features.typed_command_palette %}
        Optional<SFMPanelEntryInteractionSessionService.Session> captured =
                SFMPanelEntryInteractionSessionService.create(this, hit);
        if (captured.isEmpty()) return false;
        SFMPanelEntryInteractionSessionService.Session session = captured.orElseThrow();
        SFMClientActionContext context = new SFMClientActionContext(
                this,
                () -> !closing && Minecraft.getInstance().screen == this,
                hit.entryId()
        );
        try {
            SFMCommandPaletteScreen.openChoices(
                    context,
                    Component.literal("Panel entry " + session.oneBasedIndex()
                            + " of " + session.entryCount() + " · " + session.stableId()),
                    SFMWorkspaceLifecycleActionIds.panelEntryChoices(session),
                    () -> SFMPanelEntryInteractionSessionService.invalidate(session)
            );
            return true;
        } catch (RuntimeException failure) {
            SFMPanelEntryInteractionSessionService.invalidate(session);
            throw failure;
        }
{% else %}
        return false;
{% endif %}
    }
{% endif %}
{% if features.workspace_panel_entry_controls %}

    private void renderPanelEntryTooltip(PoseStack poseStack, int mouseX, int mouseY) {
{% if features.workspace_notifications %}
        if (workspaceToastAt(mouseX, mouseY).isPresent()) return;
{% endif %}
        panelEntryAt(mouseX, mouseY).ifPresent(hit -> renderComponentTooltip(
                poseStack,
                List.of(
                        Component.literal("Panel entry " + hit.oneBasedIndex() + " of " + hit.entryCount()),
                        hit.capturedPanel().title(),
                        Component.literal("Stable id: " + hit.stableId()),
                        Component.literal("Middle click: actions · Middle drag: move"),
                        Component.literal("Alt + middle drag content: move · Right: actions")
                ),
                mouseX,
                mouseY
        ));
    }
{% endif %}
{% if features.workspace_panel_tooltips %}

    private void renderPanelTooltip(PoseStack poseStack, int mouseX, int mouseY) {
{% if features.workspace_notifications %}
{% if features.workspace_panel_entry_controls %}
        if (workspaceToastAt(mouseX, mouseY).isPresent()
                || panelEntryAt(mouseX, mouseY).isPresent()) return;
{% else %}
{% if features.workspace_notifications %}
        if (workspaceToastAt(mouseX, mouseY).isPresent()) return;
{% endif %}
{% if features.workspace_panel_entry_controls %}
        if (panelEntryAt(mouseX, mouseY).isPresent()) return;
{% endif %}
{% endif %}
{% else %}
{% if features.workspace_notifications %}
        if (workspaceToastAt(mouseX, mouseY).isPresent()) return;
{% endif %}
{% if features.workspace_panel_entry_controls %}
        if (panelEntryAt(mouseX, mouseY).isPresent()) return;
{% endif %}
{% endif %}
{% if features.workspace_panel_metadata or features.workspace_stack_controls or features.workspace_dividers or features.workspace_directional_resize or features.workspace_layout_focus_fix %}
        List<SFMWorkspaceLayout.PanelEntry> visible = layout.visiblePanels();
{% else %}
        List<SFMWorkspaceLayout.PanelEntry> visible = layout.panels();
{% endif %}
        for (int index = visible.size() - 1; index >= 0; index--) {
            SFMWorkspaceLayout.PanelEntry entry = visible.get(index);
            SFMScreenPanelBounds bounds = panelBounds.get(entry.id());
            if (bounds == null || !contains(bounds, mouseX, mouseY)) continue;
            Optional<SFMPanelTooltip> tooltip = entry.panel().tooltipAt(mouseX, mouseY);
            if (tooltip.isPresent()) {
                renderComponentTooltip(poseStack, tooltip.orElseThrow().lines(), mouseX, mouseY);
            }
            return;
        }
    }
{% endif %}
{% if features.workspace_panel_tooltips %}

    private static boolean contains(SFMScreenPanelBounds bounds, double x, double y) {
        return x >= bounds.x() && x < bounds.x() + bounds.width()
                && y >= bounds.y() && y < bounds.y() + bounds.height();
    }
{% endif %}
{% if features.workspace_dividers %}

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
{% endif %}
{% if features.workspace_notifications %}

    private void renderWorkspaceToasts(PoseStack poseStack, int mouseX, int mouseY) {
        List<SFMWorkspaceToastQueue.Snapshot> snapshots = toastQueue().snapshots();
        if (snapshots.isEmpty()) {
            workspaceToastHitRegions = List.of();
            toastQueue().setHovered(null);
            return;
        }

        int textWidth = Math.max(24, Math.min(420, this.width - 32));
        ArrayList<ToastRenderData> renderData = new ArrayList<>();
        ArrayList<SFMWorkspaceToastLayout.Measure> measures = new ArrayList<>();
        for (SFMWorkspaceToastQueue.Snapshot snapshot : snapshots) {
            SFMWorkspaceToastContent content = toastContents().get(snapshot.id());
            Component message = content == null ? Component.literal(snapshot.text()) : content.component();
            if (!message.getString().equals(snapshot.text())) message = Component.literal(snapshot.text());
            ArrayList<FormattedCharSequence> lines = new ArrayList<>(
                    this.font.split(message, textWidth));
            if (lines.isEmpty()) lines.add(Component.literal(" ").getVisualOrderText());
            if (lines.size() > TOAST_MAX_LINES) {
                lines.subList(TOAST_MAX_LINES - 1, lines.size()).clear();
                lines.add(Component.literal("…").getVisualOrderText());
            }
            int widest = lines.stream().mapToInt(this.font::width).max().orElse(1);
            int boxWidth = Math.min(Math.max(1, this.width - 4), widest + 16);
            int boxHeight = lines.size() * this.font.lineHeight + 12;
            ToastRenderData data = new ToastRenderData(snapshot, List.copyOf(lines), boxWidth, boxHeight);
            renderData.add(data);
            measures.add(new SFMWorkspaceToastLayout.Measure(snapshot.id(), boxWidth, boxHeight));
        }

        List<SFMWorkspaceToastLayout.Bounds> bounds = toastLayout().place(this.width, this.height, measures);
        SFMWorkspaceToastQueue.ToastId hovered = bounds.stream()
                .filter(candidate -> candidate.contains(mouseX, mouseY))
                .map(SFMWorkspaceToastLayout.Bounds::id)
                .reduce((first, second) -> second)
                .orElse(null);
        toastQueue().setHovered(hovered);

        Map<SFMWorkspaceToastQueue.ToastId, SFMWorkspaceToastQueue.Snapshot> updated =
                new java.util.HashMap<>();
        toastQueue().snapshots().forEach(snapshot -> updated.put(snapshot.id(), snapshot));
        Map<SFMWorkspaceToastQueue.ToastId, ToastRenderData> dataById = new java.util.HashMap<>();
        renderData.forEach(data -> dataById.put(data.snapshot().id(), data));

        ArrayList<ToastHitRegion> hitRegions = new ArrayList<>();
        for (SFMWorkspaceToastLayout.Bounds base : bounds) {
            ToastRenderData data = dataById.get(base.id());
            SFMWorkspaceToastQueue.Snapshot snapshot = updated.get(base.id());
            if (data == null || snapshot == null) continue;
            int left = Math.max(2, Math.min(
                    Math.max(2, this.width - base.width() - 2),
                    base.x() + snapshot.shakeOffset()));
            SFMWorkspaceToastLayout.Bounds painted = new SFMWorkspaceToastLayout.Bounds(
                    base.id(), left, base.y(), base.width(), base.height());
            hitRegions.add(new ToastHitRegion(base.id(), painted, data.lines()));
            renderWorkspaceToast(poseStack, data.lines(), snapshot, painted);
        }
        workspaceToastHitRegions = List.copyOf(hitRegions);
    }
{% endif %}
{% if features.workspace_notifications %}

    private @Nullable Style workspaceToastStyleAt(double mouseX, double mouseY) {
        if (font == null || workspaceToastHitRegions == null) return null;
        for (ToastHitRegion hit : workspaceToastHitRegions) {
            if (!hit.bounds().contains(mouseX, mouseY) || toastQueue().snapshot(hit.id()).isEmpty()) continue;
            double y = mouseY - hit.bounds().y() - 5;
            double x = mouseX - hit.bounds().x() - 8;
            if (y < 0 || x < 0) return null;
            int line = (int) (y / font.lineHeight);
            if (line >= hit.lines().size() || x >= font.width(hit.lines().get(line))) return null;
            return font.getSplitter().componentStyleAtWidth(hit.lines().get(line), (int) x);
        }
        return null;
    }
{% endif %}
{% if features.workspace_notifications %}

    private void renderWorkspaceToast(
            PoseStack poseStack,
            List<FormattedCharSequence> lines,
            SFMWorkspaceToastQueue.Snapshot snapshot,
            SFMWorkspaceToastLayout.Bounds bounds
    ) {
        int alpha = Math.max(0, Math.min(255, Math.round(snapshot.opacity() * 255.0F)));
        int left = bounds.x();
        int top = bounds.y();
        int right = left + bounds.width();
        int bottom = top + bounds.height();
        fill(poseStack, left, top, right, bottom, withAlpha(0x20252B, alpha));
        fill(poseStack, left, top, right, top + 1, withAlpha(0x55FFFF, alpha));
        fill(poseStack, left, top, left + 1, bottom, withAlpha(0x55FFFF, alpha));
        fill(poseStack, right - 1, top, right, bottom, withAlpha(0x55FFFF, alpha));
        int textY = top + 5;
        for (FormattedCharSequence line : lines) {
            SFMFontUtils.draw(poseStack, this.font, line, left + 8, textY,
                    withAlpha(0xFFFFFF, alpha), true);
            textY += this.font.lineHeight;
        }

        int barTop = bottom - 2;
        fill(poseStack, left, barTop, right, bottom, withAlpha(0x30404A, alpha));
        int remainingWidth = (int) Math.round(bounds.width() * snapshot.remainingFraction());
        if (remainingWidth > 0) {
            fill(poseStack, left, barTop, Math.min(right, left + remainingWidth), bottom,
                    withAlpha(0x55FFFF, alpha));
        }
    }
{% endif %}
{% if features.workspace_notifications %}

    private Optional<SFMWorkspaceToastQueue.ToastId> workspaceToastAt(double mouseX, double mouseY) {
        List<ToastHitRegion> hitRegions = workspaceToastHitRegions;
        if (hitRegions == null) return Optional.empty();
        for (int index = hitRegions.size() - 1; index >= 0; index--) {
            ToastHitRegion hit = hitRegions.get(index);
            if (hit.bounds().contains(mouseX, mouseY)
                    && toastQueue().snapshot(hit.id()).isPresent()) return Optional.of(hit.id());
        }
        return Optional.empty();
    }
{% endif %}
{% if features.workspace_notifications %}
{% if features.workspace_toast_actions %}
{% if features.typed_command_palette %}

    private boolean openWorkspaceToastActions(SFMWorkspaceToastQueue.ToastId id) {
        Optional<SFMWorkspaceToastQueue.Snapshot> snapshot = toastQueue().snapshot(id);
        if (snapshot.isEmpty()) return false;
        Optional<SFMWorkspaceToastQueue.InteractionLease> acquired =
                toastQueue().acquireInteractionLease(id);
        if (acquired.isEmpty()) return false;
        SFMWorkspaceToastQueue.InteractionLease lease = acquired.orElseThrow();
        SFMClientActionContext context = SFMClientActionContext.create(
                this,
                () -> !closing && Minecraft.getInstance().screen == this
        );
        try {
            SFMCommandPaletteScreen palette = SFMCommandPaletteScreen.openChoices(
                    context,
                    Component.literal("Notification " + id.value()),
                    workspaceToastChoicesWithPaths(snapshot.orElseThrow()),
                    lease::close
            );
            lease.onToastRemoved(palette::dismissActionSurface);
            return true;
        } catch (RuntimeException failure) {
            lease.close();
            throw failure;
        }
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_notifications %}
{% if features.workspace_toast_actions %}

    static List<SFMActionChoice> workspaceToastChoices(SFMWorkspaceToastQueue.Snapshot snapshot) {
        String id = snapshot.id().commandArgument();
        return List.of(
                SFMActionChoice.invoke(COPY_TOAST, id),
                SFMActionChoice.invoke(
                        snapshot.pinned() ? RESUME_TOAST_TIMER : STOP_TOAST_TIMER,
                        id),
                SFMActionChoice.invoke(DISMISS_TOAST, id)
        );
    }
{% endif %}
{% endif %}
{% if features.workspace_notifications %}
{% if features.workspace_toast_actions %}

    List<SFMActionChoice> workspaceToastChoicesWithPaths(SFMWorkspaceToastQueue.Snapshot snapshot) {
        ArrayList<SFMActionChoice> choices = new ArrayList<>();
        var content = toastContents().get(snapshot.id());
        if (content != null && content.details().isPresent()) choices.add(SFMActionChoice.invoke(
                new ResourceLocation("sfm", "toast/details/copy"), snapshot.id().commandArgument(),
                "Copy complete notification details"));
        if (workspaceToastAdditionalActions != null) {
            workspaceToastAdditionalActions.keySet().retainAll(toastQueue().activeIds());
            choices.addAll(workspaceToastAdditionalActions.getOrDefault(snapshot.id(), List.of()));
        }
{% if features.workspace_toast_path_actions %}
        choices.addAll(SFMToastPathAction.choices(snapshot.id(), workspaceToastPaths(snapshot.id())));
{% endif %}
        choices.addAll(workspaceToastChoices(snapshot));
        return List.copyOf(choices);
    }
{% endif %}
{% endif %}
{% if features.workspace_notifications or features.workspace_toast_actions %}

    static String workspaceToastActionDraft(
            ResourceLocation actionId,
            SFMWorkspaceToastQueue.ToastId toastId
    ) {
        return actionId + " " + toastId.commandArgument();
    }
{% endif %}
{% if features.workspace_notifications or features.workspace_toast_actions %}

    private SFMWorkspaceToastQueue toastQueue() {
        if (workspaceToasts == null) workspaceToasts = new SFMWorkspaceToastQueue();
        return workspaceToasts;
    }
{% endif %}
{% if features.workspace_notifications %}

    private SFMWorkspaceToastLayout toastLayout() {
        if (workspaceToastLayout == null) workspaceToastLayout = new SFMWorkspaceToastLayout();
        return workspaceToastLayout;
    }
{% endif %}
{% if features.workspace_notifications %}

    private static int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }
{% endif %}
{% if features.workspace_notifications %}

    private record ToastRenderData(
            SFMWorkspaceToastQueue.Snapshot snapshot,
            List<FormattedCharSequence> lines,
            int width,
            int height
    ) {
    }
{% endif %}
{% if features.workspace_notifications %}

    private record ToastHitRegion(
            SFMWorkspaceToastQueue.ToastId id,
            SFMWorkspaceToastLayout.Bounds bounds,
            List<FormattedCharSequence> lines
    ) {
    }
{% endif %}
{% if features.workspace_panel_metadata or features.workspace_widget_hosts or features.workspace_panel_measurement %}
{% else %}

    private SFMScreenPanelBounds contentBounds(SFMWorkspacePanelId panelId) {
        SFMScreenPanelBounds bounds = panelBounds.get(panelId);
        return bounds == null ? new SFMScreenPanelBounds(0, 0, 0, 0) : bounds.inset(1);
    }
{% endif %}
{% if features.workspace_panel_metadata or features.workspace_widget_hosts %}
{% else %}

    @MCVersionDependentBehaviour
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    private static void enableScissor(SFMScreenPanelBounds bounds) {
        var window = Minecraft.getInstance().getWindow();
        double scale = window.getGuiScale();
        int left = (int) Math.floor(bounds.x() * scale);
        int right = (int) Math.ceil((bounds.x() + bounds.width()) * scale);
        int top = (int) Math.floor(bounds.y() * scale);
        int bottom = (int) Math.ceil((bounds.y() + bounds.height()) * scale);
        RenderSystem.enableScissor(
                left,
                window.getHeight() - bottom,
                Math.max(0, right - left),
                Math.max(0, bottom - top)
        );
{% when "26.1.2" %}
    private static void enableScissor(GuiGraphicsExtractor graphics, SFMScreenPanelBounds bounds) {
        graphics.enableScissor(bounds.x(), bounds.y(),
                bounds.x() + bounds.width(), bounds.y() + bounds.height());
{% endcase %}
    }
{% endif %}
{% if features.workspace_panel_metadata or features.workspace_widget_hosts %}

{% endif %}
}
