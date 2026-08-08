package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceSide;
import ca.teamdman.sfm.client.terminal.SFMTerminalTuningOperation;
import net.minecraftforge.eventbus.api.IEventBus;

public final class SFMCommandPaletteActions {
    private static final SFMDeferredRegister<SFMClientAction<?>> REGISTERER =
            SFMClientActions.createContributor(SFM.MOD_ID);

    public static final SFMRegistryObject<SFMClientAction<?>, OpenCommandPaletteAction> OPEN = REGISTERER.register(
            "palette/open",
            OpenCommandPaletteAction::new
    );

    public static final SFMRegistryObject<SFMClientAction<?>, DumpRegistriesAction> DUMP_REGISTRIES = REGISTERER.register(
            "dump_registries",
            DumpRegistriesAction::new
    );

    public static final SFMRegistryObject<SFMClientAction<?>, CommandPaletteHelpAction> HELP = REGISTERER.register(
            "help",
            CommandPaletteHelpAction::new
    );

    public static final SFMRegistryObject<SFMClientAction<?>, EchoAction> ECHO = REGISTERER.register(
            "echo",
            EchoAction::new
    );

    public static final SFMRegistryObject<SFMClientAction<?>, OpenPanelAction> OPEN_PANEL = REGISTERER.register(
            "panel/open",
            () -> new OpenPanelAction(OpenPanelAction.Direction.FOCUSED)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, OpenPanelAction> OPEN_PANEL_LEFT = REGISTERER.register(
            "panel/open/left",
            () -> new OpenPanelAction(OpenPanelAction.Direction.LEFT)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, OpenPanelAction> OPEN_PANEL_RIGHT = REGISTERER.register(
            "panel/open/right",
            () -> new OpenPanelAction(OpenPanelAction.Direction.RIGHT)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, OpenPanelAction> OPEN_PANEL_ABOVE = REGISTERER.register(
            "panel/open/above",
            () -> new OpenPanelAction(OpenPanelAction.Direction.ABOVE)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, OpenPanelAction> OPEN_PANEL_BELOW = REGISTERER.register(
            "panel/open/below",
            () -> new OpenPanelAction(OpenPanelAction.Direction.BELOW)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, ClosePanelAction> CLOSE_PANEL = REGISTERER.register(
            "panel/close",
            ClosePanelAction::new
    );

    public static final SFMRegistryObject<SFMClientAction<?>, FocusPanelAction> FOCUS_PANEL_NEXT = REGISTERER.register(
            "panel/focus/next",
            () -> new FocusPanelAction(FocusPanelAction.Operation.NEXT)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, FocusPanelAction> FOCUS_PANEL_PREVIOUS = REGISTERER.register(
            "panel/focus/previous",
            () -> new FocusPanelAction(FocusPanelAction.Operation.PREVIOUS)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, FocusPanelAction> FOCUS_PANEL_INDEX = REGISTERER.register(
            "panel/focus/index",
            () -> new FocusPanelAction(FocusPanelAction.Operation.INDEX)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, ToggleMaximizePanelAction> TOGGLE_MAXIMIZE_PANEL = REGISTERER.register(
            "panel/maximize/toggle",
            ToggleMaximizePanelAction::new
    );

    public static final SFMRegistryObject<SFMClientAction<?>, OpenPanelDiagnosticsAction> OPEN_PANEL_DIAGNOSTICS = REGISTERER.register(
            "panel/diagnostics/open",
            OpenPanelDiagnosticsAction::new
    );

    public static final SFMRegistryObject<SFMClientAction<?>, CloseScreenAction> CLOSE_SCREEN = REGISTERER.register(
            "screen/close",
            CloseScreenAction::new
    );

    public static final SFMRegistryObject<SFMClientAction<?>, ClosePaletteAction> CLOSE_PALETTE = REGISTERER.register(
            "palette/close",
            ClosePaletteAction::new
    );

    public static final SFMRegistryObject<SFMClientAction<?>, MovePanelAction> MOVE_PANEL_LEFT = REGISTERER.register(
            "panel/move/left",
            () -> new MovePanelAction(SFMWorkspaceSide.LEFT)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, MovePanelAction> MOVE_PANEL_RIGHT = REGISTERER.register(
            "panel/move/right",
            () -> new MovePanelAction(SFMWorkspaceSide.RIGHT)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, MovePanelAction> MOVE_PANEL_ABOVE = REGISTERER.register(
            "panel/move/above",
            () -> new MovePanelAction(SFMWorkspaceSide.ABOVE)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, MovePanelAction> MOVE_PANEL_BELOW = REGISTERER.register(
            "panel/move/below",
            () -> new MovePanelAction(SFMWorkspaceSide.BELOW)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, DuplicatePanelAction> DUPLICATE_PANEL_LEFT = REGISTERER.register(
            "panel/duplicate/left",
            () -> new DuplicatePanelAction(SFMWorkspaceSide.LEFT)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, DuplicatePanelAction> DUPLICATE_PANEL_RIGHT = REGISTERER.register(
            "panel/duplicate/right",
            () -> new DuplicatePanelAction(SFMWorkspaceSide.RIGHT)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, DuplicatePanelAction> DUPLICATE_PANEL_ABOVE = REGISTERER.register(
            "panel/duplicate/above",
            () -> new DuplicatePanelAction(SFMWorkspaceSide.ABOVE)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, DuplicatePanelAction> DUPLICATE_PANEL_BELOW = REGISTERER.register(
            "panel/duplicate/below",
            () -> new DuplicatePanelAction(SFMWorkspaceSide.BELOW)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, ResizePanelAction> RESIZE_PANEL_LEFT = REGISTERER.register(
            "panel/resize/left",
            () -> new ResizePanelAction(SFMWorkspaceSide.LEFT)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, ResizePanelAction> RESIZE_PANEL_RIGHT = REGISTERER.register(
            "panel/resize/right",
            () -> new ResizePanelAction(SFMWorkspaceSide.RIGHT)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, ResizePanelAction> RESIZE_PANEL_ABOVE = REGISTERER.register(
            "panel/resize/above",
            () -> new ResizePanelAction(SFMWorkspaceSide.ABOVE)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, ResizePanelAction> RESIZE_PANEL_BELOW = REGISTERER.register(
            "panel/resize/below",
            () -> new ResizePanelAction(SFMWorkspaceSide.BELOW)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, PanelScaleAction> SET_PANEL_SCALE = REGISTERER.register(
            "panel/scale/set",
            () -> new PanelScaleAction(PanelScaleAction.Operation.SET)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, PanelScaleAction> INCREASE_PANEL_SCALE = REGISTERER.register(
            "panel/scale/increase",
            () -> new PanelScaleAction(PanelScaleAction.Operation.INCREASE)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, PanelScaleAction> DECREASE_PANEL_SCALE = REGISTERER.register(
            "panel/scale/decrease",
            () -> new PanelScaleAction(PanelScaleAction.Operation.DECREASE)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, PanelScaleAction> CLEAR_PANEL_SCALE = REGISTERER.register(
            "panel/scale/clear",
            () -> new PanelScaleAction(PanelScaleAction.Operation.CLEAR)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, RotatePanelAction> ROTATE_CONTENT_LEFT = REGISTERER.register(
            "panel/rotate/content/left",
            () -> new RotatePanelAction(RotatePanelAction.Kind.CONTENT, RotatePanelAction.Direction.LEFT)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, RotatePanelAction> ROTATE_CONTENT_RIGHT = REGISTERER.register(
            "panel/rotate/content/right",
            () -> new RotatePanelAction(RotatePanelAction.Kind.CONTENT, RotatePanelAction.Direction.RIGHT)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, RotatePanelAction> ROTATE_SCALE_LEFT = REGISTERER.register(
            "panel/rotate/scale/left",
            () -> new RotatePanelAction(RotatePanelAction.Kind.SCALE, RotatePanelAction.Direction.LEFT)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, RotatePanelAction> ROTATE_SCALE_RIGHT = REGISTERER.register(
            "panel/rotate/scale/right",
            () -> new RotatePanelAction(RotatePanelAction.Kind.SCALE, RotatePanelAction.Direction.RIGHT)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, OpenKeyBindingScreenAction> MANAGE_KEY_BINDINGS = REGISTERER.register(
            "keybindings/manage",
            OpenKeyBindingScreenAction::new
    );

    public static final SFMRegistryObject<SFMClientAction<?>, OpenMinecraftControlsAction> OPEN_MINECRAFT_CONTROLS = REGISTERER.register(
            "controls/open",
            OpenMinecraftControlsAction::new
    );

    public static final SFMRegistryObject<SFMClientAction<?>, ManagerEditAction> MANAGER_EDIT = REGISTERER.register(
            "manager/edit",
            ManagerEditAction::new
    );

    public static final SFMRegistryObject<SFMClientAction<?>, SFMGuiScaleAction> SET_GUI_SCALE = REGISTERER.register(
            "ui/gui_scale/set",
            () -> new SFMGuiScaleAction(SFMGuiScaleAction.Operation.SET)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, SFMGuiScaleAction> INCREMENT_GUI_SCALE = REGISTERER.register(
            "ui/gui_scale/increment",
            () -> new SFMGuiScaleAction(SFMGuiScaleAction.Operation.INCREMENT)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, SFMGuiScaleAction> DECREMENT_GUI_SCALE = REGISTERER.register(
            "ui/gui_scale/decrement",
            () -> new SFMGuiScaleAction(SFMGuiScaleAction.Operation.DECREMENT)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, OpenReplAction> OPEN_REPL = REGISTERER.register(
            "repl/open",
            OpenReplAction::new
    );

    public static final SFMRegistryObject<SFMClientAction<?>, ConnectRustServerAction> CONNECT_RUST_SERVER = REGISTERER.register(
            "terminal/server/connect",
            ConnectRustServerAction::new
    );

    public static final SFMRegistryObject<SFMClientAction<?>, StartRustServerAction> START_RUST_SERVER = REGISTERER.register(
            "terminal/server/start",
            StartRustServerAction::new
    );

    public static final SFMRegistryObject<SFMClientAction<?>, SetTerminalTransportAction> SET_TERMINAL_TRANSPORT = REGISTERER.register(
            "terminal/transport/set",
            SetTerminalTransportAction::new
    );

    public static final SFMRegistryObject<SFMClientAction<?>, SetTerminalRendererAction> SET_TERMINAL_RENDERER = REGISTERER.register(
            "terminal/renderer/set",
            SetTerminalRendererAction::new
    );

    public static final SFMRegistryObject<SFMClientAction<?>, ToggleTerminalPresentationAction> TOGGLE_TERMINAL_PRESENTATION = REGISTERER.register(
            "terminal/presentation/toggle",
            ToggleTerminalPresentationAction::new
    );

    public static final SFMRegistryObject<SFMClientAction<?>, TerminalPropertiesAction> TERMINAL_SURFACE_AUTO = REGISTERER.register(
            "terminal/properties/surface/auto",
            () -> new TerminalPropertiesAction(SFMTerminalTuningOperation.SURFACE_AUTO)
    );
    public static final SFMRegistryObject<SFMClientAction<?>, TerminalPropertiesAction> TERMINAL_SURFACE_SET = REGISTERER.register(
            "terminal/properties/surface/set",
            () -> new TerminalPropertiesAction(SFMTerminalTuningOperation.SURFACE_SET)
    );
    public static final SFMRegistryObject<SFMClientAction<?>, TerminalPropertiesAction> TERMINAL_SURFACE_WIDTH_INCREASE = REGISTERER.register(
            "terminal/properties/surface/width/increase",
            () -> new TerminalPropertiesAction(SFMTerminalTuningOperation.SURFACE_WIDTH_INCREASE)
    );
    public static final SFMRegistryObject<SFMClientAction<?>, TerminalPropertiesAction> TERMINAL_SURFACE_WIDTH_DECREASE = REGISTERER.register(
            "terminal/properties/surface/width/decrease",
            () -> new TerminalPropertiesAction(SFMTerminalTuningOperation.SURFACE_WIDTH_DECREASE)
    );
    public static final SFMRegistryObject<SFMClientAction<?>, TerminalPropertiesAction> TERMINAL_SURFACE_HEIGHT_INCREASE = REGISTERER.register(
            "terminal/properties/surface/height/increase",
            () -> new TerminalPropertiesAction(SFMTerminalTuningOperation.SURFACE_HEIGHT_INCREASE)
    );
    public static final SFMRegistryObject<SFMClientAction<?>, TerminalPropertiesAction> TERMINAL_SURFACE_HEIGHT_DECREASE = REGISTERER.register(
            "terminal/properties/surface/height/decrease",
            () -> new TerminalPropertiesAction(SFMTerminalTuningOperation.SURFACE_HEIGHT_DECREASE)
    );
    public static final SFMRegistryObject<SFMClientAction<?>, TerminalPropertiesAction> TERMINAL_FONT_AUTO = REGISTERER.register(
            "terminal/properties/font/auto",
            () -> new TerminalPropertiesAction(SFMTerminalTuningOperation.FONT_AUTO)
    );
    public static final SFMRegistryObject<SFMClientAction<?>, TerminalPropertiesAction> TERMINAL_FONT_SET = REGISTERER.register(
            "terminal/properties/font/set",
            () -> new TerminalPropertiesAction(SFMTerminalTuningOperation.FONT_SET)
    );
    public static final SFMRegistryObject<SFMClientAction<?>, TerminalPropertiesAction> TERMINAL_FONT_INCREASE = REGISTERER.register(
            "terminal/properties/font/increase",
            () -> new TerminalPropertiesAction(SFMTerminalTuningOperation.FONT_INCREASE)
    );
    public static final SFMRegistryObject<SFMClientAction<?>, TerminalPropertiesAction> TERMINAL_FONT_DECREASE = REGISTERER.register(
            "terminal/properties/font/decrease",
            () -> new TerminalPropertiesAction(SFMTerminalTuningOperation.FONT_DECREASE)
    );
    public static final SFMRegistryObject<SFMClientAction<?>, TerminalPropertiesAction> TERMINAL_CELLS_AUTO = REGISTERER.register(
            "terminal/properties/cells/auto",
            () -> new TerminalPropertiesAction(SFMTerminalTuningOperation.CELLS_AUTO)
    );
    public static final SFMRegistryObject<SFMClientAction<?>, TerminalPropertiesAction> TERMINAL_CELLS_SET = REGISTERER.register(
            "terminal/properties/cells/set",
            () -> new TerminalPropertiesAction(SFMTerminalTuningOperation.CELLS_SET)
    );
    public static final SFMRegistryObject<SFMClientAction<?>, TerminalPropertiesAction> TERMINAL_COLUMNS_INCREASE = REGISTERER.register(
            "terminal/properties/cells/columns/increase",
            () -> new TerminalPropertiesAction(SFMTerminalTuningOperation.COLUMNS_INCREASE)
    );
    public static final SFMRegistryObject<SFMClientAction<?>, TerminalPropertiesAction> TERMINAL_COLUMNS_DECREASE = REGISTERER.register(
            "terminal/properties/cells/columns/decrease",
            () -> new TerminalPropertiesAction(SFMTerminalTuningOperation.COLUMNS_DECREASE)
    );
    public static final SFMRegistryObject<SFMClientAction<?>, TerminalPropertiesAction> TERMINAL_ROWS_INCREASE = REGISTERER.register(
            "terminal/properties/cells/rows/increase",
            () -> new TerminalPropertiesAction(SFMTerminalTuningOperation.ROWS_INCREASE)
    );
    public static final SFMRegistryObject<SFMClientAction<?>, TerminalPropertiesAction> TERMINAL_ROWS_DECREASE = REGISTERER.register(
            "terminal/properties/cells/rows/decrease",
            () -> new TerminalPropertiesAction(SFMTerminalTuningOperation.ROWS_DECREASE)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, SFMThemeAction> THEME_RELOAD = REGISTERER.register(
            "theme/reload",
            () -> new SFMThemeAction(SFMThemeAction.Operation.RELOAD)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, SFMThemeAction> THEME_RESTORE_DEFAULTS = REGISTERER.register(
            "theme/restore_defaults",
            () -> new SFMThemeAction(SFMThemeAction.Operation.RESTORE_DEFAULTS)
    );

    public static final SFMRegistryObject<SFMClientAction<?>, SFMThemeAction> THEME_OPEN_FILE = REGISTERER.register(
            "theme/open_file",
            () -> new SFMThemeAction(SFMThemeAction.Operation.OPEN_FILE)
    );
    public static final SFMRegistryObject<SFMClientAction<?>, OpenThemeSettingsAction> THEME_SETTINGS = REGISTERER.register(
            "theme/settings", OpenThemeSettingsAction::new
    );

    private SFMCommandPaletteActions() {
    }

    public static void register(IEventBus bus) {
        REGISTERER.register(bus);
    }
}
