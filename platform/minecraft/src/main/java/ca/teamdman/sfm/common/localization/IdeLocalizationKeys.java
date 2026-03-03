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
