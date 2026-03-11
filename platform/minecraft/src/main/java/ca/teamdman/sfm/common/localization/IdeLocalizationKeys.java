package ca.teamdman.sfm.common.localization;

import ca.teamdman.sfm.SFM;

import java.util.ArrayList;
import java.util.List;

public final class IdeLocalizationKeys {
    public static final LocalizationEntry IDE_PANEL_EXPLORER_TITLE = new LocalizationEntry(
            "gui.sfm.ide.panel.explorer.title",
            "Explorer"
    );
    public static final LocalizationEntry IDE_PANEL_TERMINAL_TITLE = new LocalizationEntry(
            "gui.sfm.ide.panel.terminal.title",
            "Terminal"
    );
    public static final LocalizationEntry IDE_EXPLORER_MANAGER_NODE = new LocalizationEntry(
            "gui.sfm.ide.explorer.node.manager",
            "Manager"
    );
    public static final LocalizationEntry IDE_EXPLORER_PLAYER_NODE = new LocalizationEntry(
            "gui.sfm.ide.explorer.node.player",
            "Player"
    );
    public static final LocalizationEntry IDE_EXPLORER_SLOT_NODE = new LocalizationEntry(
            "gui.sfm.ide.explorer.node.slot",
            "Slot %s"
    );
    public static final LocalizationEntry IDE_EXPLORER_MAIN_HAND_SLOT_NODE = new LocalizationEntry(
            "gui.sfm.ide.explorer.node.main_hand_slot",
            "Main hand: Slot %s"
    );
    public static final LocalizationEntry IDE_EXPLORER_CLICKED_SLOT_NODE = new LocalizationEntry(
            "gui.sfm.ide.explorer.node.clicked_slot",
            "Clicked: Slot %s"
    );
    public static final LocalizationEntry IDE_EXPLORER_CLICKED_SLOT_NONE = new LocalizationEntry(
            "gui.sfm.ide.explorer.node.clicked_slot.none",
            "Clicked: none"
    );
    public static final LocalizationEntry IDE_PLAYER_INVENTORY_LABEL = new LocalizationEntry(
            "gui.sfm.ide.player_inventory.label",
            "Player Inventory"
    );
    public static final LocalizationEntry IDE_TERMINAL_INPUT_PLACEHOLDER = new LocalizationEntry(
            "gui.sfm.ide.terminal.input.placeholder",
            "Type command..."
    );
    public static final LocalizationEntry IDE_TERMINAL_PREFIX_HINT = new LocalizationEntry(
            "gui.sfm.ide.terminal.prefix_hint",
            "IDE mode: /sfm ide"
    );
    public static final LocalizationEntry IDE_CENTER_TAB_INVENTORY = new LocalizationEntry(
            "gui.sfm.ide.center.tab.inventory",
            "Inventory"
    );
    public static final LocalizationEntry IDE_CENTER_TAB_TEXT_EDITOR = new LocalizationEntry(
            "gui.sfm.ide.center.tab.text_editor",
            "Text"
    );
    public static final LocalizationEntry IDE_CENTER_TEXT_EDITOR_TITLE = new LocalizationEntry(
            "gui.sfm.ide.center.text_editor.title",
            "Disk Preview (slot %s)"
    );
    public static final LocalizationEntry IDE_CENTER_TEXT_EDITOR_EMPTY = new LocalizationEntry(
            "gui.sfm.ide.center.text_editor.empty",
            "Select a disk in Explorer"
    );
    public static final LocalizationEntry IDE_PLAYGROUND_TITLE = new LocalizationEntry(
            "gui.sfm.ide.playground.title",
            "SFM IDE Playground"
    );
    public static final LocalizationEntry IDE_PLAYGROUND_SUBTITLE = new LocalizationEntry(
            "gui.sfm.ide.playground.subtitle",
            "Hyprland-inspired dock layout playground"
    );
    public static final LocalizationEntry IDE_PLAYGROUND_PANEL_SHELL = new LocalizationEntry(
            "gui.sfm.ide.playground.panel.shell",
            "Shell Context"
    );
    public static final LocalizationEntry IDE_PLAYGROUND_PANEL_WORKSPACE = new LocalizationEntry(
            "gui.sfm.ide.playground.panel.workspace",
            "Workspace"
    );
    public static final LocalizationEntry IDE_PLAYGROUND_PANEL_LAYOUT = new LocalizationEntry(
            "gui.sfm.ide.playground.panel.layout",
            "Layout"
    );
    public static final LocalizationEntry IDE_PLAYGROUND_PANEL_TERMINAL = new LocalizationEntry(
            "gui.sfm.ide.playground.panel.terminal",
            "Terminal"
    );
    public static final LocalizationEntry IDE_PLAYGROUND_LABEL_PLAYER = new LocalizationEntry(
            "gui.sfm.ide.playground.label.player",
            "Player: %s"
    );
    public static final LocalizationEntry IDE_PLAYGROUND_LABEL_LOOK = new LocalizationEntry(
            "gui.sfm.ide.playground.label.look",
            "Look: %s"
    );
    public static final LocalizationEntry IDE_PLAYGROUND_LABEL_HIT = new LocalizationEntry(
            "gui.sfm.ide.playground.label.hit",
            "Hit: %s"
    );
    public static final LocalizationEntry IDE_PLAYGROUND_LABEL_DIMENSION = new LocalizationEntry(
            "gui.sfm.ide.playground.label.dimension",
            "Dimension: %s"
    );
    public static final LocalizationEntry IDE_PLAYGROUND_LABEL_FOCUS = new LocalizationEntry(
            "gui.sfm.ide.playground.label.focus",
            "Focused panel: %s"
    );
    public static final LocalizationEntry IDE_PLAYGROUND_LABEL_TARGET = new LocalizationEntry(
            "gui.sfm.ide.playground.label.target",
            "Focused target: %s"
    );
    public static final LocalizationEntry IDE_PLAYGROUND_LABEL_SELECTION = new LocalizationEntry(
            "gui.sfm.ide.playground.label.selection",
            "Selected targets: %s"
    );
    public static final LocalizationEntry IDE_PLAYGROUND_LABEL_HINTS = new LocalizationEntry(
            "gui.sfm.ide.playground.label.hints",
            "Tab: cycle focus | Alt+Shift+Arrows: resize focused edge | %s: close"
    );
    public static final LocalizationEntry IDE_PLAYGROUND_LABEL_LAYOUT_MODE = new LocalizationEntry(
            "gui.sfm.ide.playground.label.layout_mode",
            "Ordered edge carving"
    );
    public static final LocalizationEntry IDE_PLAYGROUND_LABEL_LAYOUT_PIECES = new LocalizationEntry(
            "gui.sfm.ide.playground.label.layout_pieces",
            "Pieces: shell left, layout right, terminal down, workspace center"
    );
    public static final LocalizationEntry IDE_PLAYGROUND_LABEL_TERMINAL = new LocalizationEntry(
            "gui.sfm.ide.playground.label.terminal",
            "Terminal/result surface placeholder"
    );
    public static final LocalizationEntry IDE_PLAYGROUND_TERMINAL_PLACEHOLDER = new LocalizationEntry(
            "gui.sfm.ide.playground.terminal.placeholder",
            "Run action id or /sfm ide ..."
    );
    public static final LocalizationEntry IDE_PLAYGROUND_TERMINAL_HINT = new LocalizationEntry(
            "gui.sfm.ide.playground.terminal.hint",
            "Action bus terminal. Try exact ids, panel.*, selection.*, or /sfm ide ..."
    );
    public static final LocalizationEntry IDE_PLAYGROUND_TERMINAL_EMPTY = new LocalizationEntry(
            "gui.sfm.ide.playground.terminal.empty",
            "Try: sfm:panel.toggle_layout | sfm:panel.toggle_terminal | sfm:selection.select_focused"
    );
    public static final LocalizationEntry IDE_PLAYGROUND_TERMINAL_SUCCESS = new LocalizationEntry(
            "gui.sfm.ide.playground.terminal.success",
            "Ran: %s"
    );
    public static final LocalizationEntry IDE_PLAYGROUND_TERMINAL_UNKNOWN = new LocalizationEntry(
            "gui.sfm.ide.playground.terminal.unknown",
            "Unknown action: %s"
    );
    public static final LocalizationEntry IDE_PLAYGROUND_LABEL_WORKSPACE = new LocalizationEntry(
            "gui.sfm.ide.playground.label.workspace",
            "Dock workspace preview"
    );

    public static final LocalizationEntry IDE_KEY_OPEN_PLAYGROUND = new LocalizationEntry(
            "key.sfm.ide.open_playground",
            "IDE - Open Playground"
    );
    public static final LocalizationEntry IDE_KEY_TOGGLE_RIGHT_PANEL = new LocalizationEntry(
            "key.sfm.ide.toggle_right_panel",
            "IDE - Toggle Right Panel"
    );
    public static final LocalizationEntry IDE_KEY_TOGGLE_BOTTOM_PANEL = new LocalizationEntry(
            "key.sfm.ide.toggle_bottom_panel",
            "IDE - Toggle Bottom Panel"
    );
    public static final LocalizationEntry IDE_KEY_FOCUS_EXPLORER_PANEL = new LocalizationEntry(
            "key.sfm.ide.focus_explorer_panel",
            "IDE - Focus Explorer"
    );

    public static List<LocalizationEntry> getEntries() {
        var rtn = new ArrayList<LocalizationEntry>();
        for (var field : IdeLocalizationKeys.class.getFields()) {
            if (field.getType() == LocalizationEntry.class) {
                try {
                    rtn.add((LocalizationEntry) field.get(null));
                } catch (IllegalAccessException e) {
                    SFM.LOGGER.error("Failed reading entry field", e);
                }
            }
        }
        return rtn;
    }
}
