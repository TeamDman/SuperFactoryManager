package ca.teamdman.sfm.common.localization;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.registry.SFMItems;

import java.util.ArrayList;
import java.util.List;

public final class LocalizationKeys {
    public static final LocalizationEntry TEXT_EDIT_SCREEN_TITLE = new LocalizationEntry(
            "gui.sfm.text_editor.title",
            "Text Editor"
    );
    public static final LocalizationEntry LOGS_SCREEN_TITLE = new LocalizationEntry(
            "gui.sfm.logs.title",
            "Logs"
    );
    public static final LocalizationEntry PROGRAM_EDIT_SCREEN_DONE_BUTTON_TOOLTIP = new LocalizationEntry(
            "gui.sfm.text_editor.done_button.tooltip",
            "Shift+Enter to submit"
    );
    public static final LocalizationEntry PROGRAM_EDIT_SCREEN_CONFIG_BUTTON_TOOLTIP = new LocalizationEntry(
            "gui.sfm.text_editor.config_button.tooltip",
            "Open editor config"
    );
    public static final LocalizationEntry SAVE_CHANGES_CONFIRM_SCREEN_TITLE = new LocalizationEntry(
            "gui.sfm.save_changes_confirm.title",
            "Save changes"
    );
    public static final LocalizationEntry SAVE_CHANGES_CONFIRM_SCREEN_MESSAGE = new LocalizationEntry(
            "gui.sfm.save_changes_confirm.message",
            "Do you want to save before exiting?"
    );
    public static final LocalizationEntry SAVE_CHANGES_CONFIRM_SCREEN_YES_BUTTON = new LocalizationEntry(
            "gui.sfm.save_changes_confirm.yes_button",
            "Overwrite disk"
    );
    public static final LocalizationEntry SAVE_CHANGES_CONFIRM_SCREEN_NO_BUTTON = new LocalizationEntry(
            "gui.sfm.save_changes_confirm.no_button",
            "Continue editing"
    );
    public static final LocalizationEntry REMOVE_ACTIVE_LABEL_CONFIRM_SCREEN_TITLE = new LocalizationEntry(
            "gui.sfm.remove_active_label_confirm.title",
            "Remove label: %s"
    );
    public static final LocalizationEntry REMOVE_ACTIVE_LABEL_CONFIRM_SCREEN_MESSAGE = new LocalizationEntry(
            "gui.sfm.remove_active_label_confirm.message",
            "Are you sure you want to remove the label \"%s\" from %d blocks?"
    );
    public static final LocalizationEntry REMOVE_ALL_LABELS_CONFIRM_SCREEN_TITLE = new LocalizationEntry(
            "gui.sfm.remove_all_labels_confirm.title",
            "Remove ALL labels"
    );
    public static final LocalizationEntry REMOVE_ALL_LABELS_CONFIRM_SCREEN_MESSAGE = new LocalizationEntry(
            "gui.sfm.remove_all_labels_confirm.message",
            "Are you sure you want to remove %d labels from %d blocks?"
    );
    public static final LocalizationEntry MANAGER_RESET_CONFIRM_SCREEN_TITLE = new LocalizationEntry(
            "gui.sfm.manager.reset_confirm_screen.title",
            "Reset disk?"
    );
    public static final LocalizationEntry MANAGER_RESET_CONFIRM_SCREEN_MESSAGE = new LocalizationEntry(
            "gui.sfm.manager.reset_confirm_screen.message",
            "Are you sure you want to reset this disk?"
    );
    public static final LocalizationEntry MANAGER_RESET_CONFIRM_SCREEN_YES_BUTTON = new LocalizationEntry(
            "gui.sfm.manager.reset_confirm_screen.yes_button",
            "Wipe program and labels"
    );
    public static final LocalizationEntry MANAGER_RESET_CONFIRM_SCREEN_NO_BUTTON = new LocalizationEntry(
            "gui.sfm.manager.reset_confirm_screen.no_button",
            "Never mind, make no changes"
    );
    public static final LocalizationEntry MANAGER_PASTE_CONFIRM_SCREEN_TITLE = new LocalizationEntry(
            "gui.sfm.manager.paste_confirm_screen.title",
            "Paste from clipboard?"
    );
    public static final LocalizationEntry MANAGER_PASTE_CONFIRM_SCREEN_MESSAGE = new LocalizationEntry(
            "gui.sfm.manager.paste_confirm_screen.message",
            "Are you sure you want to overwrite this disk?"
    );
    public static final LocalizationEntry MANAGER_PASTE_CONFIRM_SCREEN_YES_BUTTON = new LocalizationEntry(
            "gui.sfm.manager.paste_confirm_screen.yes_button",
            "Paste clipboard"
    );
    public static final LocalizationEntry MANAGER_PASTE_CONFIRM_SCREEN_NO_BUTTON = new LocalizationEntry(
            "gui.sfm.manager.paste_confirm_screen.no_button",
            "Never mind, make no changes"
    );
    public static final LocalizationEntry EXIT_WITHOUT_SAVING_CONFIRM_SCREEN_TITLE = new LocalizationEntry(
            "gui.sfm.exit_without_saving_confirm.title",
            "Exit without saving?"
    );
    public static final LocalizationEntry EXIT_WITHOUT_SAVING_CONFIRM_SCREEN_MESSAGE = new LocalizationEntry(
            "gui.sfm.exit_without_saving_confirm.message",
            "Are you sure you want to abandon your work?"
    );
    public static final LocalizationEntry EXIT_WITHOUT_SAVING_CONFIRM_SCREEN_YES_BUTTON = new LocalizationEntry(
            "gui.sfm.exit_without_saving_confirm.yes_button",
            "Exit without saving"
    );
    public static final LocalizationEntry EXIT_WITHOUT_SAVING_CONFIRM_SCREEN_NO_BUTTON = new LocalizationEntry(
            "gui.sfm.exit_without_saving_confirm.no_button",
            "Continue editing"
    );
    public static final LocalizationEntry FACADE_CONFIRM_CLEAR_SCREEN_TITLE = new LocalizationEntry(
            "gui.sfm.facade_confirm_clear.title",
            "Are you sure you want to clear these facades?"
    );
    public static final LocalizationEntry FACADE_CONFIRM_CLEAR_SCREEN_MESSAGE = new LocalizationEntry(
            "gui.sfm.facade_confirm_clear.message",
            "%d different facade states across %d blocks will be wiped from the world."
    );
    public static final LocalizationEntry FACADE_CONFIRM_CHANGE_WORLD_BLOCK_SCREEN_TITLE = new LocalizationEntry(
            "gui.sfm.facade_confirm_change_world_block.title",
            "Are you sure you want to change the facade world block?"
    );
    public static final LocalizationEntry FACADE_CONFIRM_CHANGE_WORLD_BLOCK_SCREEN_MESSAGE = new LocalizationEntry(
            "gui.sfm.facade_confirm_change_world_block.message",
            "%d blocks will change shape, their facades will be persisted."
    );
    public static final LocalizationEntry FACADE_CONFIRM_APPLY_SCREEN_TITLE = new LocalizationEntry(
            "gui.sfm.facade_confirm_apply.title",
            "Are you sure you want to update the facade appearance?"
    );
    public static final LocalizationEntry FACADE_CONFIRM_APPLY_SCREEN_MESSAGE = new LocalizationEntry(
            "gui.sfm.facade_confirm_apply.message",
            "%d different facade states across %d blocks that will be overwritten."
    );
    public static final LocalizationEntry CONFIRM_FUNNY_YES_1 = new LocalizationEntry(
            "gui.sfm.confirm.funny.yes.1",
            "Yeah, sure, why not."
    );
    public static final LocalizationEntry CONFIRM_FUNNY_YES_2 = new LocalizationEntry(
            "gui.sfm.confirm.funny.yes.2",
            "Sure, what could go wrong."
    );
    public static final LocalizationEntry CONFIRM_FUNNY_YES_3 = new LocalizationEntry(
            "gui.sfm.confirm.funny.yes.3",
            "Yup, go ahead."
    );
    public static final LocalizationEntry CONFIRM_FUNNY_YES_4 = new LocalizationEntry(
            "gui.sfm.confirm.funny.yes.4",
            "Go for it."
    );
    public static final LocalizationEntry CONFIRM_FUNNY_YES_5 = new LocalizationEntry(
            "gui.sfm.confirm.funny.yes.5",
            "lol do it"
    );
    public static final LocalizationEntry CONFIRM_FUNNY_YES_6 = new LocalizationEntry(
            "gui.sfm.confirm.funny.yes.6",
            "Apply the change."
    );
    public static final LocalizationEntry CONFIRM_FUNNY_NO_1 = new LocalizationEntry(
            "gui.sfm.confirm.funny.no.1",
            "Nah, changed my mind"
    );
    public static final LocalizationEntry CONFIRM_FUNNY_NO_2 = new LocalizationEntry(
            "gui.sfm.confirm.funny.no.2",
            "Holy guacamole, no"
    );
    public static final LocalizationEntry CONFIRM_FUNNY_NO_3 = new LocalizationEntry(
            "gui.sfm.confirm.funny.no.3",
            "no no no no no"
    );
    public static final LocalizationEntry CONFIRM_FUNNY_NO_4 = new LocalizationEntry(
            "gui.sfm.confirm.funny.no.4",
            "Nope, not today"
    );
    public static final LocalizationEntry CONFIRM_FUNNY_NO_5 = new LocalizationEntry(
            "gui.sfm.confirm.funny.no.5",
            "ABORT ABORT ABORT"
    );
    public static final LocalizationEntry CONFIRM_FUNNY_NO_6 = new LocalizationEntry(
            "gui.sfm.confirm.funny.no.6",
            "Never mind"
    );
    public static final LocalizationEntry PROGRAM_WARNING_RESOURCE_EACH_WITHOUT_PATTERN = new LocalizationEntry(
            "program.sfm.warnings.each_without_pattern",
            "EACH used without a pattern, statement %s"
    );
    @SuppressWarnings("unused") // used by minecraft without us having to directly reference
    public static final LocalizationEntry MOD_NAME = new LocalizationEntry(
            "mod.name",
            "Super Factory Manager"
    );
    public static final LocalizationEntry CREATIVE_TAB = new LocalizationEntry(
            "item_group.sfm",
            "Super Factory Manager"
    );
    @SuppressWarnings("unused") // used by minecraft without us having to directly reference
    public static final LocalizationEntry CABLE_BLOCK = new LocalizationEntry(
            () -> SFMBlocks.CABLE_BLOCK.get().getDescriptionId(),
            () -> "Inventory Cable"
    );
    @SuppressWarnings("unused") // used by minecraft without us having to directly reference
    public static final LocalizationEntry MANAGER_BLOCK = new LocalizationEntry(
            () -> SFMBlocks.MANAGER_BLOCK.get().getDescriptionId(),
            () -> "Factory Manager"
    );
    @SuppressWarnings("unused") // used by minecraft without us having to directly reference
    public static final LocalizationEntry PRINTING_PRESS_BLOCK = new LocalizationEntry(
            () -> SFMBlocks.PRINTING_PRESS_BLOCK.get().getDescriptionId(),
            () -> "Printing Press"
    );
    public static final LocalizationEntry PRINTING_PRESS_JEI_CATEGORY_TITLE = new LocalizationEntry(
            "gui.jei.category.sfm.printing_press",
            "Printing Press"
    );
    public static final LocalizationEntry FALLING_ANVIL_JEI_CATEGORY_TITLE = new LocalizationEntry(
            "gui.jei.category.sfm.falling_anvil",
            "Falling Anvil"
    );
    public static final LocalizationEntry FALLING_ANVIL_JEI_CONSUMED = new LocalizationEntry(
            "gui.jei.category.sfm.falling_anvil.consumed",
            "Gets consumed"
    );
    public static final LocalizationEntry FALLING_ANVIL_JEI_NOT_CONSUMED = new LocalizationEntry(
            "gui.jei.category.sfm.falling_anvil.not_consumed",
            "Not consumed"
    );
    public static final LocalizationEntry PRINTING_PRESS_TOOLTIP = new LocalizationEntry(
            () -> SFMItems.PRINTING_PRESS_ITEM.get().getDescriptionId() + ".tooltip",
            () -> "Place with an air gap below a downward facing piston. Extend the piston to use."
    );
    public static final LocalizationEntry DISK_ITEM_TOOLTIP_LABEL_HEADER = new LocalizationEntry(
            () -> SFMItems.DISK_ITEM.get().getDescriptionId() + ".tooltip.label_section.header",
            () -> "Labels"
    );
    public static final LocalizationEntry DISK_ITEM_TOOLTIP_LABEL = new LocalizationEntry(
            () -> SFMItems.DISK_ITEM.get().getDescriptionId() + ".tooltip.label_section.entry",
            () -> " - %s: %d blocks"
    );
    public static final LocalizationEntry LABEL_GUN_CHAT_PULLED = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN_ITEM.get().getDescriptionId() + ".chat.pulled",
            () -> "Pulled labels from the manager. Release the label gun pull modifier key (%s) to push instead."
    );
    public static final LocalizationEntry LABEL_GUN_CHAT_PUSHED = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN_ITEM.get().getDescriptionId() + ".chat.pushed",
            () -> "Pushed labels to the manager. Hold the label gun pull modifier key (%s) to pull instead."
    );
    public static final LocalizationEntry LABEL_GUN_CHAT_SKIPPED_BLOCKS = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN_ITEM.get().getDescriptionId() + ".chat.skipped_blocks",
            () -> "Skipped %d blocks not touching cables"
    );
    public static final LocalizationEntry NETWORK_TOOL_ITEM_TOOLTIP_1 = new LocalizationEntry(
            () -> SFMItems.NETWORK_TOOL_ITEM.get().getDescriptionId() + ".tooltip.1",
            () -> "Shows cables through walls when held."
    );
    public static final LocalizationEntry NETWORK_TOOL_ITEM_TOOLTIP_2 = new LocalizationEntry(
            () -> SFMItems.NETWORK_TOOL_ITEM.get().getDescriptionId() + ".tooltip.2",
            () -> "Right click a block face to view diagnostic info."
    );
    public static final LocalizationEntry NETWORK_TOOL_ITEM_TOOLTIP_3 = new LocalizationEntry(
            () -> SFMItems.NETWORK_TOOL_ITEM.get().getDescriptionId() + ".tooltip.3",
            () -> "You might not need this, don't forget you can press %s in an inventory to toggle the inspector."
    );
    public static final LocalizationEntry NETWORK_TOOL_ITEM_TOOLTIP_4 = new LocalizationEntry(
            () -> SFMItems.NETWORK_TOOL_ITEM.get().getDescriptionId() + ".tooltip.4",
            () -> "Place in off-hand with block in main hand and right-click cable to set facade."
    );
    public static final LocalizationEntry NETWORK_TOOL_ITEM_TOOLTIP_5 = new LocalizationEntry(
            () -> SFMItems.NETWORK_TOOL_ITEM.get().getDescriptionId() + ".tooltip.5",
            () -> "Ctrl-click to facade contiguously."
    );
    public static final LocalizationEntry NETWORK_TOOL_ITEM_TOOLTIP_6 = new LocalizationEntry(
            () -> SFMItems.NETWORK_TOOL_ITEM.get().getDescriptionId() + ".tooltip.6",
            () -> "Alt-click to facade matching block across the network."
    );
    public static final LocalizationEntry NETWORK_TOOL_ITEM_TOOLTIP_7 = new LocalizationEntry(
            () -> SFMItems.NETWORK_TOOL_ITEM.get().getDescriptionId() + ".tooltip.7",
            () -> "Ctrl-alt-click to facade entire network."
    );
    public static final LocalizationEntry LABEL_GUN_ITEM_TOOLTIP_TOGGLE_LABEL_REMINDER = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN_ITEM.get().getDescriptionId() + ".tooltip.toggle_label_reminder",
            () -> "%s a block to toggle the active label on it."
    );
    public static final LocalizationEntry LABEL_GUN_ITEM_TOOLTIP_PUSH_REMINDER = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN_ITEM.get().getDescriptionId() + ".tooltip.push_reminder",
            () -> "%s a Factory Manager to push labels to it."
    );
    public static final LocalizationEntry LABEL_GUN_ITEM_TOOLTIP_PULL_REMINDER = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN_ITEM.get().getDescriptionId() + ".tooltip.pull_reminder",
            () -> "%s + %s a Factory Manager to pull labels from it."
    );
    public static final LocalizationEntry LABEL_GUN_ITEM_TOOLTIP_CLEAR_REMINDER = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN_ITEM.get().getDescriptionId() + ".tooltip.clear_reminder",
            () -> "%s + %s a block to remove labels from it."
    );
    public static final LocalizationEntry LABEL_GUN_ITEM_TOOLTIP_PICK_REMINDER = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN_ITEM.get().getDescriptionId() + ".tooltip.pick_reminder",
            () -> "%s + %s a block to pick the active label from it."
    );
    public static final LocalizationEntry LABEL_GUN_ITEM_TOOLTIP_CONTIGUOUS_REMINDER = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN_ITEM.get().getDescriptionId() + ".tooltip.contiguous_reminder",
            () -> "Hold %s to perform changes against contiguous blocks touching cables."
    );
    public static final LocalizationEntry LABEL_GUN_ITEM_TOOLTIP_CYCLE_VIEW_REMINDER = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN_ITEM.get().getDescriptionId() + ".tooltip.cycle_view_reminder",
            () -> "Press %s to cycle label view."
    );
    public static final LocalizationEntry LABEL_GUN_ITEM_TOOLTIP_NEXT_REMINDER = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN_ITEM.get().getDescriptionId() + ".tooltip.next_reminder",
            () -> "Press %s to select next label."
    );
    public static final LocalizationEntry LABEL_GUN_ITEM_TOOLTIP_PREVIOUS_REMINDER = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN_ITEM.get().getDescriptionId() + ".tooltip.previous_reminder",
            () -> "Press %s to select previous label."
    );
    public static final LocalizationEntry LABEL_GUN_ITEM_TOOLTIP_SCROLL_REMINDER = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN_ITEM.get().getDescriptionId() + ".tooltip.scroll_reminder",
            () -> "%s + mouse wheel to select next/previous label."
    );
    public static final LocalizationEntry LABEL_GUN_ITEM_TOOLTIP_GUI_REMINDER = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN_ITEM.get().getDescriptionId() + ".tooltip.gui_reminder",
            () -> "%s the air to open GUI."
    );
    public static final LocalizationEntry LABEL_GUN_ITEM_TOOLTIP_TARGET_MANAGER_REMINDER = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN_ITEM.get().getDescriptionId() + ".tooltip.target_manager_reminder",
            () -> "%s + %s to label a Factory Manager itself."
    );
    public static final LocalizationEntry LABEL_GUN_VIEW_MODE_SHOW_ONLY_ACTIVE_AND_TARGETED = new LocalizationEntry(
            () -> "sfm.label_gun.view_mode.show_only_active_and_targeted",
            () -> "Showing blocks with active label. Cycle mode in gui or with %s"
    );
    public static final LocalizationEntry LABEL_GUN_VIEW_MODE_SHOW_ONLY_TARGETED = new LocalizationEntry(
            () -> "sfm.label_gun.view_mode.show_only_targeted",
            () -> "Showing only targeted block labels. Cycle mode in gui or with %s"
    );
    public static final LocalizationEntry NETWORK_TOOL_REMINDER_OVERLAY = new LocalizationEntry(
            () -> "sfm.network_tool.reminder_overlay",
            () -> "Toggle network tool overlay with %s"
    );
    public static final LocalizationEntry LABEL_GUN_ITEM_NAME_WITH_LABEL = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN_ITEM.get().getDescriptionId() + ".with_label",
            () -> "Label Gun: \"%s\""
    );
    public static final LocalizationEntry WATER_TANK_ITEM_TOOLTIP_1 = new LocalizationEntry(
            () -> SFMBlocks.WATER_TANK_BLOCK.get().getDescriptionId() + ".tooltip.1",
            () -> "Requires two adjacent water sources."
    );
    public static final LocalizationEntry WATER_TANK_ITEM_TOOLTIP_2 = new LocalizationEntry(
            () -> SFMBlocks.WATER_TANK_BLOCK.get().getDescriptionId() + ".tooltip.2",
            () -> "More effective when also adjacent to other active water tanks."
    );
    public static final LocalizationEntry LABEL_GUN_GUI_TITLE = new LocalizationEntry(
            "gui.sfm.title.labelgun",
            "Label Gun"
    );
    public static final LocalizationEntry EXAMPLES_GUI_WARNING_1 = new LocalizationEntry(
            "gui.sfm.program_template_picker.warning1",
            "Hitting \"Done\" will on the next screen will overwrite your existing program!"
    );
    public static final LocalizationEntry EXAMPLES_GUI_WARNING_2 = new LocalizationEntry(
            "gui.sfm.program_template_picker.warning2",
            "Hit <esc> to cancel instead."
    );
    public static final LocalizationEntry EXAMPLES_GUI_TITLE = new LocalizationEntry(
            "gui.sfm.title.program_template_picker",
            "Program Template Picker"
    );
    public static final LocalizationEntry INTELLISENSE_PICK_LIST_GUI_TITLE = new LocalizationEntry(
            "gui.sfm.title.intellisense_pick_list",
            "Intellisense Pick List"
    );
    public static final LocalizationEntry LABEL_GUN_GUI_LABEL_PLACEHOLDER = new LocalizationEntry(
            "gui.sfm.label_gun.placeholder",
            "Label"
    );
    public static final LocalizationEntry LABEL_GUN_GUI_LABEL_BUTTON = new LocalizationEntry(
            "gui.sfm.label_gun.label_button",
            "%s (%d)"
    );
    public static final LocalizationEntry LABEL_GUN_GUI_LABEL_EDIT_PLACEHOLDER = new LocalizationEntry(
            "gui.sfm.label_gun.label_edit_placeholder",
            "Search or enter new label"
    );
    public static final LocalizationEntry LABEL_GUN_GUI_PRUNE_BUTTON = new LocalizationEntry(
            "gui.sfm.label_gun.prune_button",
            "Prune"
    );
    public static final LocalizationEntry LABEL_GUN_GUI_CLEAR_BUTTON = new LocalizationEntry(
            "gui.sfm.label_gun.clear_button",
            "Clear"
    );
    public static final LocalizationEntry LABEL_GUN_GUI_CYCLE_VIEW_BUTTON = new LocalizationEntry(
            "gui.sfm.label_gun.button.toggle_label_view",
            "Cycle label view"
    );
    public static final LocalizationEntry DISK_EDIT_IN_HAND_TOOLTIP = new LocalizationEntry(
            "gui.sfm.disk.tooltip.edit_in_hand",
            "You can right-click a disk in your hand to edit outside of a manager."
    );
    public static final LocalizationEntry MANAGER_GUI_PASTE_FROM_CLIPBOARD_BUTTON_TOOLTIP = new LocalizationEntry(
            "gui.sfm.manager.tooltip.paste",
            "Press Ctrl+V to paste."
    );
    public static final LocalizationEntry MANAGER_GUI_EDIT_BUTTON_TOOLTIP = new LocalizationEntry(
            "gui.sfm.manager.edit_button.tooltip",
            "Press %s to edit."
    );
    public static final LocalizationEntry MANAGER_GUI_EDIT_BUTTON = new LocalizationEntry(
            "gui.sfm.manager.edit_button",
            "Edit"
    );
    public static final LocalizationEntry MANAGER_GUI_RESET_BUTTON_TOOLTIP = new LocalizationEntry(
            "gui.sfm.manager.tooltip.reset",
            "Wipes ALL disk data."
    );
    public static final LocalizationEntry MANAGER_CONTAINER = new LocalizationEntry(
            "container.sfm.manager",
            "Factory Manager"
    );
    public static final LocalizationEntry TEST_BARREL_TANK_CONTAINER = new LocalizationEntry(
            "container.sfm.test_barrel_tank",
            "Test Barrel Tank"
    );
    public static final LocalizationEntry PROGRAM_WARNING_UNUSED_LABEL = new LocalizationEntry(
            "program.sfm.warnings.unused_label",
            "Label \"%s\" is used in code but not assigned in the world."
    );
    public static final LocalizationEntry PROGRAM_WARNING_OUTPUT_RESOURCE_TYPE_NOT_FOUND_IN_INPUTS = new LocalizationEntry(
            "program.sfm.warnings.output_label_not_found_in_inputs",
            "Statement \"%s\" at %s uses resource type \"%s\" which has no matching input statement."
    );
    public static final LocalizationEntry PROGRAM_WARNING_UNUSED_INPUT_LABEL = new LocalizationEntry(
            "program.sfm.warnings.unused_input_label",
            "Statement \"%s\" at %s inputs \"%s\" from \"%s\" but no future output statement consume \"%s\"."
    );
    public static final LocalizationEntry PROGRAM_WARNING_UNKNOWN_RESOURCE_ID = new LocalizationEntry(
            "program.sfm.warnings.unknown_resource_id",
            "Resource \"%s\" was not found."
    );
    public static final LocalizationEntry PROGRAM_WARNING_UNDEFINED_LABEL = new LocalizationEntry(
            "program.sfm.warnings.undefined_label",
            "Label \"%s\" is assigned in the world but not defined in code."
    );
    public static final LocalizationEntry PROGRAM_REMINDER_PUSH_LABELS = new LocalizationEntry(
            "program.sfm.reminders.push_labels",
            "Did you remember to push your labels using the label gun?"
    );
    public static final LocalizationEntry PROGRAM_WARNING_MEKANISM_USED_WITHOUT_DIRECTION = new LocalizationEntry(
            "program.sfm.warnings.mekanism_used_without_direction",
            "Mekanism blocks are read-only when no side is specified, check label \"%s\" used in \"%s\""
    );
    public static final LocalizationEntry PROGRAM_WARNING_MEKANISM_BAD_SIDE_CONFIG = new LocalizationEntry(
            "program.sfm.warnings.mekanism_bad_side_config",
            "Mekanism block side config at %s doesn't agree with statement, check label \"%s\" used in \"%s\""
    );
    public static final LocalizationEntry PROGRAM_WARNING_DISCONNECTED_LABEL = new LocalizationEntry(
            "program.sfm.warnings.disconnected_label",
            "Label \"%s\" is assigned in the world at %s but not connected by cables."
    );
    public static final LocalizationEntry PROGRAM_WARNING_CONNECTED_BUT_NOT_VIABLE_LABEL = new LocalizationEntry(
            "program.sfm.warnings.adjacent_but_disconnected_label",
            "Label \"%s\" is assigned in the world at %s and is connected by cables but is not detected as a valid inventory."
    );
    public static final LocalizationEntry PROGRAM_WARNING_ROUND_ROBIN_SMELLY_EACH = new LocalizationEntry(
            "program.sfm.warnings.round_robin_smelly_each",
            "Round robin by block shouldn't be used with EACH, statement %s"
    );
    public static final LocalizationEntry PROGRAM_WARNING_ROUND_ROBIN_SMELLY_COUNT = new LocalizationEntry(
            "program.sfm.warnings.round_robin_smelly_count",
            "Round robin by label should be used with more than one label, statement %s"
    );
    public static final LocalizationEntry PROGRAM_ERROR_COMPILE_FAILED = new LocalizationEntry(
            "program.sfm.error.compile_failed",
            "Failed to compile."
    );
    public static final LocalizationEntry PROGRAM_ERROR_LITERAL = new LocalizationEntry(
            "program.sfm.error.literal",
            "%s"
    );
    public static final LocalizationEntry PROGRAM_COMPILE_FAILED_WITH_ERRORS = new LocalizationEntry(
            "program.sfm.error.compile_failed_with_errors",
            "Failed to compile with %d errors."
    );
    public static final LocalizationEntry PROGRAM_COMPILE_SUCCEEDED_WITH_WARNINGS = new LocalizationEntry(
            "program.sfm.error.compile_success_with_warnings",
            "Successfully compiled \"%s\" with %d warnings."
    );
    public static final LocalizationEntry PROGRAM_COMPILE_FROM_DISK_BEGIN = new LocalizationEntry(
            "program.sfm.compile_begin",
            "Compiling program from disk."
    );
    public static final LocalizationEntry PROGRAM_TICK_TIME_MS = new LocalizationEntry(
            "program.sfm.tick.time",
            "Program tick took %.2f ms"
    );
    public static final LocalizationEntry PROGRAM_TICK_STATEMENT_TIME_MS = new LocalizationEntry(
            "program.sfm.tick.time_taken.statement",
            "Program statement tick took %.2f ms:\n```\n%s\n```\n"
    );
    public static final LocalizationEntry PROGRAM_TICK_TRIGGER_TIME_MS = new LocalizationEntry(
            "program.sfm.tick.time_taken.trigger",
            "Program trigger tick took %.2f ms:\n```\n%s\n```\n"
    );
    public static final LocalizationEntry LOG_RESOURCE_TYPE_GET_CAPABILITIES_BEGIN = new LocalizationEntry(
            "log.sfm.resource_type.get_capabilities.begin",
            "Gathering capabilities of type %s (%s) against labels %s"
    );
    public static final LocalizationEntry LOG_RESOURCE_TYPE_GET_CAPABILITIES_CAP_NOT_PRESENT = new LocalizationEntry(
            "log.sfm.resource_type.get_capabilities.not_present",
            "Capability %s %s direction=%s not present"
    );
    public static final LocalizationEntry LOG_RESOURCE_TYPE_GET_CAPABILITIES_CAP_PRESENT = new LocalizationEntry(
            "log.sfm.resource_type.get_capabilities.present",
            "Capability %s %s direction=%s present"
    );
    public static final LocalizationEntry LOG_CAPABILITY_CACHE_HIT = new LocalizationEntry(
            "log.sfm.capability_cache.hit",
            "Capability cache HIT for %s %s direction=%s"
    );
    public static final LocalizationEntry LOG_CAPABILITY_CACHE_HIT_INVALID = new LocalizationEntry(
            "log.sfm.capability_cache.hit_invalid",
            "Capability cache HIT but NOT PRESENT for %s %s direction=%s"
    );
    public static final LocalizationEntry LOG_CAPABILITY_CACHE_MISS = new LocalizationEntry(
            "log.sfm.capability_cache.miss",
            "Capability cache MISS for %s %s direction=%s"
    );
    public static final LocalizationEntry LOG_PROGRAM_TICK = new LocalizationEntry(
            "log.sfm.program.tick",
            "PROGRAM TICK BEGIN"
    );
    public static final LocalizationEntry LOG_PROGRAM_CONTEXT = new LocalizationEntry(
            "log.sfm.program.context",
            "Initial program context: %s"
    );
    public static final LocalizationEntry LOG_CABLE_NETWORK_DETAILS_HEADER_1 = new LocalizationEntry(
            "log.sfm.cable_network.header.1",
            "======= Cable network ======="
    );
    public static final LocalizationEntry LOG_CABLE_NETWORK_DETAILS_HEADER_2 = new LocalizationEntry(
            "log.sfm.cable_network.header.2",
            "Cable positions:"
    );
    public static final LocalizationEntry LOG_CABLE_NETWORK_DETAILS_HEADER_3 = new LocalizationEntry(
            "log.sfm.cable_network.header.3",
            "Capability positions:"
    );
    public static final LocalizationEntry LOG_CABLE_NETWORK_DETAILS_BODY = new LocalizationEntry(
            "log.sfm.cable_network.body",
            "%s"
    );
    public static final LocalizationEntry LOG_CABLE_NETWORK_DETAILS_FOOTER = new LocalizationEntry(
            "log.sfm.cable_network.footer",
            "============================="
    );
    public static final LocalizationEntry LOG_LABEL_POSITION_HOLDER_DETAILS_HEADER = new LocalizationEntry(
            "log.sfm.label_position_holder.header",
            "=== Label position holder ==="
    );
    public static final LocalizationEntry LOG_LABEL_POSITION_HOLDER_DETAILS_BODY = new LocalizationEntry(
            "log.sfm.label_position_holder.body",
            "%s"
    );
    public static final LocalizationEntry LOG_LABEL_POSITION_HOLDER_DETAILS_FOOTER = new LocalizationEntry(
            "log.sfm.label_position_holder.footer",
            "============================="
    );
    public static final LocalizationEntry LOG_PROGRAM_VOIDED_RESOURCES = new LocalizationEntry(
            "log.sfm.program.voided_resources",
            "%s"
    );
    public static final LocalizationEntry LOG_PROGRAM_TICK_TRIGGER_STATEMENT = new LocalizationEntry(
            "log.sfm.statement.tick.trigger",
            "TRIGGERED FROM %s"
    );
    public static final LocalizationEntry LOG_PROGRAM_TICK_INPUT_STATEMENT = new LocalizationEntry(
            "log.sfm.statement.tick.input",
            "%s"
    );
    public static final LocalizationEntry LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS = new LocalizationEntry(
            "log.sfm.statement.tick.io.gather_slots",
            "Gathering slots for IO statement \n```\n%s\n```\n"
    );
    public static final LocalizationEntry LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_CACHE_HIT = new LocalizationEntry(
            "log.sfm.statement.tick.io.gather_slots.cache_hit",
            "Cache hit - this statement has already gathered slots"
    );
    public static final LocalizationEntry LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_CACHE_MISS = new LocalizationEntry(
            "log.sfm.statement.tick.io.gather_slots.cache_miss",
            "Statement cache miss - this is the first time this statement is being gathered"
    );
    public static final LocalizationEntry LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_EACH = new LocalizationEntry(
            "log.sfm.statement.tick.io.gather_slots.each",
            "EACH keyword used - trackers will be unique to each block"
    );
    public static final LocalizationEntry LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_NOT_EACH = new LocalizationEntry(
            "log.sfm.statement.tick.io.gather_slots.not_each",
            "EACH keyword not used - trackers will be shared between blocks"
    );
    public static final LocalizationEntry LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_RANGE = new LocalizationEntry(
            "log.sfm.statement.tick.io.gather_slots.range",
            "Gathering slots in range set: %s"
    );
    public static final LocalizationEntry LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_SLOT_NOT_IN_RANGE = new LocalizationEntry(
            "log.sfm.statement.tick.io.gather_slots.not_in_range",
            "Slot %d - not in range"
    );
    public static final LocalizationEntry LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_SLOT_SHOULD_NOT_CREATE = new LocalizationEntry(
            "log.sfm.statement.tick.io.gather_slots.should_not_create",
            "Slot %d - skipping - %s"
    );
    public static final LocalizationEntry LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_SLOT_CREATED = new LocalizationEntry(
            "log.sfm.statement.tick.io.gather_slots.created",
            "Slot %d - tracking - %s - %s"
    );
    public static final LocalizationEntry LOG_PROGRAM_TICK_IO_STATEMENT_MOVE_TO_BEGIN = new LocalizationEntry(
            "log.sfm.statement.tick.io.move_to.begin",
            "Begin moving %s into %s"
    );
    public static final LocalizationEntry LOG_PROGRAM_TICK_IO_STATEMENT_MOVE_TO_TYPE_MISMATCH = new LocalizationEntry(
            "log.sfm.statement.tick.io.move_to.type_mismatch",
            "Type mismatch, skipping"
    );
    public static final LocalizationEntry LOG_PROGRAM_TICK_IO_STATEMENT_MOVE_TO_DESTINATION_TRACKER_REJECT = new LocalizationEntry(
            "log.sfm.statement.tick.io.move_to.destination_tracker_reject",
            "Destination tracker rejected the transfer, skipping"
    );
    public static final LocalizationEntry LOG_PROGRAM_TICK_IO_STATEMENT_MOVE_TO_ZERO_SIMULATED_MOVEMENT = new LocalizationEntry(
            "log.sfm.statement.tick.io.move_to.zero_simulated_movement",
            "Got remainder %d after simulated insertion of potential %d (0 to move), skipping"
    );
    public static final LocalizationEntry LOG_PROGRAM_TICK_IO_STATEMENT_MOVE_TO_RETENTION_OBLIGATION = new LocalizationEntry(
            "log.sfm.statement.tick.io.move_to.retention_obligation",
            "Promised to leave %d in the source slot, still obligated to leave %d"
    );
    public static final LocalizationEntry LOG_PROGRAM_TICK_IO_STATEMENT_MOVE_TO_RETENTION_OBLIGATION_NO_MOVE = new LocalizationEntry(
            "log.sfm.statement.tick.io.move_to.retention_obligation_no_move",
            "Nothing to move after retention obligations, marking source slot done and skipping"
    );
    public static final LocalizationEntry LOG_PROGRAM_TICK_IO_STATEMENT_MOVE_TO_STACK_LIMIT_NEW_TO_MOVE = new LocalizationEntry(
            "log.sfm.statement.tick.io.move_to.stack_limit_no_move",
            "Max transferable dest=%d, source=%d, stack limit=%d; new toMove=%d"
    );
    public static final LocalizationEntry LOG_PROGRAM_TICK_IO_STATEMENT_MOVE_TO_ZERO_TO_MOVE = new LocalizationEntry(
            "log.sfm.statement.tick.io.move_to.zero_to_move",
            "toMove=0, skipping"
    );
    public static final LocalizationEntry LOG_PROGRAM_TICK_IO_STATEMENT_MOVE_TO_EXTRACTED = new LocalizationEntry(
            "log.sfm.statement.tick.io.move_to.extracted",
            "Extracted %d from slot %d"
    );
    public static final LocalizationEntry LOG_PROGRAM_TICK_IO_STATEMENT_MOVE_TO_END = new LocalizationEntry(
            "log.sfm.statement.tick.io.move_to.end",
            "Moved %d %s - source=%s, dest=%s"
    );
    public static final LocalizationEntry LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_FOR_RESOURCE_TYPE = new LocalizationEntry(
            "log.sfm.statement.tick.io.gather_slots.resource_types",
            "Gathering for: %s (%s)"
    );
    public static final LocalizationEntry LOG_PROGRAM_TICK_OUTPUT_STATEMENT = new LocalizationEntry(
            "log.sfm.statement.tick.output",
            "%s"
    );
    public static final LocalizationEntry LOG_MANAGER_CABLE_NETWORK_REBUILD = new LocalizationEntry(
            "log.sfm.manager.cable_network_rebuild",
            "User performed cable network rebuild"
    );
    public static final LocalizationEntry LOG_PROGRAM_TICK_OUTPUT_STATEMENT_DISCOVERED_INPUT_SLOT_COUNT = new LocalizationEntry(
            "log.sfm.statement.tick.output.discovered_input_slot_count",
            "Discovered %d input slots"
    );
    public static final LocalizationEntry LOG_PROGRAM_TICK_OUTPUT_STATEMENT_DISCOVERED_OUTPUT_SLOT_COUNT = new LocalizationEntry(
            "log.sfm.statement.tick.output.discovered_output_slot_count",
            "Discovered %d output slots"
    );
    public static final LocalizationEntry LOG_PROGRAM_TICK_OUTPUT_STATEMENT_SHORT_CIRCUIT_NO_INPUT_SLOTS = new LocalizationEntry(
            "log.sfm.statement.tick.output.short_circuit_no_input_slots",
            "No input slots, skipping"
    );
    public static final LocalizationEntry LOG_PROGRAM_TICK_OUTPUT_STATEMENT_SHORT_CIRCUIT_NO_OUTPUT_SLOTS = new LocalizationEntry(
            "log.sfm.statement.tick.output.short_circuit_no_output_slots",
            "No output slots, skipping"
    );
    public static final LocalizationEntry LOG_PROGRAM_TICK_FORGET_STATEMENT = new LocalizationEntry(
            "log.sfm.statement.tick.forget",
            "FORGET %s"
    );
    public static final LocalizationEntry LOG_PROGRAM_TICK_IF_STATEMENT_WAS_TRUE = new LocalizationEntry(
            "log.sfm.statement.tick.if.true",
            "TRUE: %s"
    );
    public static final LocalizationEntry LOG_PROGRAM_TICK_IF_STATEMENT_WAS_FALSE = new LocalizationEntry(
            "log.sfm.statement.tick.if.false",
            "FALSE: %s"
    );
    public static final LocalizationEntry LOG_PROGRAM_TICK_WITH_REDSTONE_COUNT = new LocalizationEntry(
            "log.sfm.program.tick.redstone_count",
            "Program ticking with %d unprocessed redstone pulses."
    );
    public static final LocalizationEntry PROGRAM_ERROR_MALFORMED_RESOURCE_TYPE = new LocalizationEntry(
            "program.sfm.error.malformed_resource_type",
            "Program has a malformed resource type \"%s\".\nReminder: Resource types must be literals, not wildcards."
    );
    public static final LocalizationEntry PROGRAM_ERROR_UNKNOWN_RESOURCE_TYPE = new LocalizationEntry(
            "program.sfm.error.unknown_resource_type",
            "Program references an unknown resource type \"%s\""
    );
    public static final LocalizationEntry PROGRAM_ERROR_DISALLOWED_RESOURCE_TYPE = new LocalizationEntry(
            "program.sfm.error.disallowed_resource_type",
            "Program references a disallowed resource type \"%s\""
    );
    public static final LocalizationEntry MANAGER_GUI_STATE_NO_PROGRAM = new LocalizationEntry(
            "gui.sfm.manager.state.no_program",
            "no program"
    );
    public static final LocalizationEntry MANAGER_GUI_STATE = new LocalizationEntry(
            "gui.sfm.manager.state",
            "State: %s"
    );
    public static final LocalizationEntry CONTAINER_INSPECTOR_SHOW_EXPORTS_BUTTON = new LocalizationEntry(
            "gui.sfm.container_inspector.show_exports_button",
            "Export Inspector"
    );
    public static final LocalizationEntry CONTAINER_INSPECTOR_MEKANISM_NULL_DIRECTION_WARNING = new LocalizationEntry(
            "gui.sfm.container_inspector.mekanism_null_direction_warning",
            "MEKANISM BLOCKS ARE READ-ONLY FROM THE NULL DIRECTION!!!!!!"
    );
    public static final LocalizationEntry CONTAINER_INSPECTOR_MEKANISM_MACHINE_INPUTS = new LocalizationEntry(
            "gui.sfm.container_inspector.mekanism_machine_inputs",
            "The following are based on the MACHINE'S input config"
    );
    public static final LocalizationEntry CONTAINER_INSPECTOR_MEKANISM_MACHINE_OUTPUTS = new LocalizationEntry(
            "gui.sfm.container_inspector.mekanism_machine_outputs",
            "The following are based on the MACHINE'S output config"
    );
    public static final LocalizationEntry CONTAINER_INSPECTOR_NOTICE_1 = new LocalizationEntry(
            "gui.sfm.container_inspector.notice.1",
            "GUI slots don't always correspond to automation slots!!!"
    );
    public static final LocalizationEntry CONTAINER_INSPECTOR_NOTICE_2 = new LocalizationEntry(
            "gui.sfm.container_inspector.notice.2",
            "Press %s to toggle this overlay."
    );
    public static final LocalizationEntry CONTAINER_INSPECTOR_CONTAINER_SLOT_COUNT = new LocalizationEntry(
            "gui.sfm.container_inspector.container_slot_count",
            "Container Slots: %d"
    );
    public static final LocalizationEntry CONTAINER_INSPECTOR_INVENTORY_SLOT_COUNT = new LocalizationEntry(
            "gui.sfm.container_inspector.inventory_slot_count",
            "Inventory Slots: %d"
    );
    public static final LocalizationEntry MANAGER_GUI_PEAK_TICK_TIME_MS = new LocalizationEntry(
            "gui.sfm.manager.peak_tick_time",
            "Peak tick time: %s ms"
    );
    public static final LocalizationEntry MANAGER_GUI_HOVERED_TICK_TIME_MS = new LocalizationEntry(
            "gui.sfm.manager.hovered_tick_time",
            "Hovered tick time: %s ms"
    );
    public static final LocalizationEntry MANAGER_GUI_STATE_NO_DISK = new LocalizationEntry(
            "gui.sfm.manager.state.no_disk",
            "missing disk"
    );
    public static final LocalizationEntry MANAGER_GUI_STATE_RUNNING = new LocalizationEntry(
            "gui.sfm.manager.state.running",
            "running"
    );
    public static final LocalizationEntry MANAGER_GUI_STATE_INVALID_PROGRAM = new LocalizationEntry(
            "gui.sfm.manager.state.invalid_program",
            "invalid program"
    );
    public static final LocalizationEntry MANAGER_GUI_PASTE_FROM_CLIPBOARD_BUTTON = new LocalizationEntry(
            "gui.sfm.manager.button.paste_clipboard",
            "Paste from clipboard"
    );
    public static final LocalizationEntry MANAGER_GUI_COPY_TO_CLIPBOARD_BUTTON = new LocalizationEntry(
            "gui.sfm.manager.button.copy_to_clipboard",
            "Copy to clipboard"
    );
    public static final LocalizationEntry MANAGER_GUI_VIEW_EXAMPLES_BUTTON = new LocalizationEntry(
            "gui.sfm.manager.button.view_examples",
            "View examples"
    );
    public static final LocalizationEntry MANAGER_GUI_VIEW_LOGS_BUTTON = new LocalizationEntry(
            "gui.sfm.manager.button.view_logs",
            "View logs"
    );
    public static final LocalizationEntry MANAGER_GUI_DISCORD_BUTTON = new LocalizationEntry(
            "gui.sfm.manager.button.discord",
            "Discord"
    );
    public static final LocalizationEntry MANAGER_GUI_REBUILD_BUTTON = new LocalizationEntry(
            "gui.sfm.manager.button.rebuild",
            "Rebuild cable network"
    );
    public static final LocalizationEntry MANAGER_GUI_SERVER_CONFIG_BUTTON = new LocalizationEntry(
            "gui.sfm.manager.button.server_config",
            "View server config"
    );
    public static final LocalizationEntry LOG_LEVEL_UPDATED = new LocalizationEntry(
            "log.sfm.level_updated",
            "Log level updated to %s"
    );
    public static final LocalizationEntry LOGS_GUI_CLEAR_LOGS_BUTTON_PACKET_RECEIVED = new LocalizationEntry(
            "gui.sfm.logs.button.clear_logs.packet_received",
            "Cleared logs"
    );
    public static final LocalizationEntry LOGS_GUI_CLEAR_LOGS_BUTTON = new LocalizationEntry(
            "gui.sfm.logs.button.clear_logs",
            "Clear logs"
    );
    public static final LocalizationEntry LOGS_GUI_COPY_LOGS_BUTTON = new LocalizationEntry(
            "gui.sfm.logs.button.copy_logs",
            "Copy logs"
    );
    public static final LocalizationEntry LOGS_GUI_COPY_LOGS_BUTTON_TOOLTIP = new LocalizationEntry(
            "gui.sfm.logs.button.copy_logs.tooltip",
            "Shift-click for raw"
    );
    public static final LocalizationEntry LOGS_GUI_NO_CONTENT = new LocalizationEntry(
            "gui.sfm.logs.no_content",
            "Ahoy, world!\nChange the log level using the buttons at the top of this screen.\nLoud log levels will be reset after a single program execution.\nLogging can make statements take longer to execute.\nThe scrolling is wacky, I'm working on it.\nOnly a few hundred lines are drawn to reduce lag.\nUse the copy button to view in a better editor for now lol."
    );
    public static final LocalizationEntry LOGS_MISSING_ADJACENT_CABLE = new LocalizationEntry(
            "gui.sfm.logs.missing_adjacent_cable",
            "No adjacent cable found for %s"
    );
    public static final LocalizationEntry LOGS_EMPTY_CAPABILITY = new LocalizationEntry(
            "gui.sfm.logs.empty_capability",
            "Received an empty capability result for %s %s direction=%s"
    );
    public static final LocalizationEntry MANAGER_GUI_VIEW_EXAMPLES_BUTTON_TOOLTIP = new LocalizationEntry(
            "gui.sfm.manager.button.view_examples.tooltip",
            "Press Ctrl+Shift+E to view examples."
    );
    public static final LocalizationEntry MANAGER_GUI_RESET_BUTTON = new LocalizationEntry(
            "gui.sfm.manager.button.reset",
            "Reset"
    );
    public static final LocalizationEntry MANAGER_GUI_WARNING_BUTTON_TOOLTIP = new LocalizationEntry(
            "gui.sfm.manager.button.warning.tooltip",
            "Click to copy code with warnings and errors.\nShift-click to attempt to fix warnings."
    );
    public static final LocalizationEntry MANAGER_GUI_WARNING_BUTTON_TOOLTIP_READ_ONLY = new LocalizationEntry(
            "gui.sfm.manager.button.warning.tooltip.read_only",
            "Click to copy code with warnings and errors."
    );
    public static final LocalizationEntry MANAGER_GUI_STATUS_LOADED_CLIPBOARD = new LocalizationEntry(
            "gui.sfm.manager.status.loaded_clipboard",
            "Loaded from clipboard!"
    );
    public static final LocalizationEntry MANAGER_GUI_STATUS_SAVED_CLIPBOARD = new LocalizationEntry(
            "gui.sfm.manager.status.saved_clipboard",
            "Saved to clipboard!"
    );
    public static final LocalizationEntry MANAGER_GUI_STATUS_RESET = new LocalizationEntry(
            "gui.sfm.manager.status.reset",
            "Reset program and labels!"
    );
    public static final LocalizationEntry MANAGER_GUI_STATUS_REBUILD = new LocalizationEntry(
            "gui.sfm.manager.status.rebuild",
            "Rebuilding cache!"
    );
    public static final LocalizationEntry MANAGER_GUI_STATUS_FIX = new LocalizationEntry(
            "gui.sfm.manager.status.fix",
            "Fixing problems!"
    );
    public static final LocalizationEntry GUI_ADVANCED_TOOLTIP_HINT = new LocalizationEntry(
            "gui.sfm.advanced.tooltip.hint",
            "Hold %s to know more."
    );
    public static final LocalizationEntry MORE_HOVER_INFO_KEY = new LocalizationEntry(
            "key.sfm.more_info",
            "Hold For More Info"
    );
    public static final LocalizationEntry CYCLE_LABEL_VIEW_KEY = new LocalizationEntry(
            "key.sfm.toggle_label_view_key",
            "Cycle label gun view"
    );
    public static final LocalizationEntry TOGGLE_NETWORK_TOOL_OVERLAY = new LocalizationEntry(
            "key.sfm.toggle_network_tool_overlay",
            "Toggle network tool overlay"
    );
    public static final LocalizationEntry CONTAINER_INSPECTOR_TOGGLE_KEY = new LocalizationEntry(
            "key.sfm.container_inspector.activation_key",
            "Toggle Container Inspector"
    );
    public static final LocalizationEntry ITEM_INSPECTOR_TOGGLE_KEY = new LocalizationEntry(
            "key.sfm.item_inspector.activation_key",
            "(WIP) Copy Hovered Item To Clipboard"
    );
    public static final LocalizationEntry LABEL_GUN_PICK_BLOCK_MODIFIER_KEY = new LocalizationEntry(
            "key.sfm.label_gun.pick_block_modifier",
            "Label Gun Pick Block Modifier"
    );
    public static final LocalizationEntry LABEL_GUN_CONTIGUOUS_MODIFIER_KEY = new LocalizationEntry(
            "key.sfm.label_gun.contiguous_modifier",
            "Label Gun Contiguous Modifier"
    );
    public static final LocalizationEntry LABEL_GUN_CLEAR_MODIFIER_KEY = new LocalizationEntry(
            "key.sfm.label_gun.clear_modifier",
            "Label Gun Clear Modifier"
    );
    public static final LocalizationEntry LABEL_GUN_NEXT_LABEL_KEY = new LocalizationEntry(
            "key.sfm.label_gun.next_label",
            "Label Gun Next Label"
    );
    public static final LocalizationEntry LABEL_GUN_PREVIOUS_LABEL_KEY = new LocalizationEntry(
            "key.sfm.label_gun.previous_label",
            "Label Gun Previous Label"
    );
    public static final LocalizationEntry LABEL_GUN_SCROLL_MODIFIER_KEY = new LocalizationEntry(
            "key.sfm.label_gun.scroll_modifier",
            "Label Gun Scroll Modifier"
    );
    public static final LocalizationEntry LABEL_GUN_PULL_MODIFIER_KEY = new LocalizationEntry(
            "key.sfm.label_gun.pull_modifier",
            "Label Gun Pull Modifier"
    );
    public static final LocalizationEntry LABEL_GUN_TARGET_MANAGER_MODIFIER_KEY = new LocalizationEntry(
            "key.sfm.label_gun.target_manager_modifier",
            "Label Gun Target Manager Modifier"
    );
    public static final LocalizationEntry MANAGER_SCREEN_OPEN_TEXT_EDITOR_KEY = new LocalizationEntry(
            "key.sfm.manager.text_editor",
            "Manager Screen - Open Text Editor"
    );
    public static final LocalizationEntry TITLE_SCREEN_OPEN_TEXT_EDITOR_KEY = new LocalizationEntry(
            "key.sfm.title_screen.text_editor",
            "Title Screen - Open Text Editor"
    );
    public static final LocalizationEntry SFM_KEY_CATEGORY = new LocalizationEntry(
            "key.categories.sfm",
            "Super Factory Manager"
    );
    public static final LocalizationEntry ITEM_INSPECTOR_COPIED_TO_CLIPBOARD = new LocalizationEntry(
            "gui.sfm.item_inspector.copied_to_clipboard",
            "Copied {} characters to clipboard!"
    );
    public static final LocalizationEntry PROGRAM_WARNING_TOO_MANY_CONDITIONS = new LocalizationEntry(
            "program.sfm.warnings.too_many_conditions",
            "Too many conditions for simulation, some linter warnings may be missed."
    );
    @SuppressWarnings("unused") // used by minecraft without us having to directly reference
    public static final LocalizationEntry CABLE_FACADE_BLOCK = new LocalizationEntry(
            () -> SFMBlocks.CABLE_FACADE_BLOCK.get().getDescriptionId(),
            () -> "Inventory Cable Facade"
    );
    @SuppressWarnings("unused") // used by minecraft without us having to directly reference
    public static final LocalizationEntry FANCY_CABLE_BLOCK = new LocalizationEntry(
            () -> SFMBlocks.FANCY_CABLE_BLOCK.get().getDescriptionId(),
            () -> "Fancy Inventory Cable"
    );
    @SuppressWarnings("unused") // used by minecraft without us having to directly reference
    public static final LocalizationEntry FANCY_CABLE_FACADE_BLOCK = new LocalizationEntry(
            () -> SFMBlocks.FANCY_CABLE_FACADE_BLOCK.get().getDescriptionId(),
            () -> "Fancy Inventory Cable Facade"
    );
    @SuppressWarnings("unused") // used by minecraft without us having to directly reference
    public static final LocalizationEntry TUNNELLED_MANAGER_BLOCK = new LocalizationEntry(
            () -> SFMBlocks.TUNNELLED_MANAGER_BLOCK.get().getDescriptionId(),
            () -> "Tunnelled Factory Manager"
    );
    @SuppressWarnings("unused") // used by minecraft without us having to directly reference
    public static final LocalizationEntry BUFFER_BLOCK = new LocalizationEntry(
            () -> SFMBlocks.BUFFER_BLOCK.get().getDescriptionId(),
            () -> "Resource Buffer"
    );
    @SuppressWarnings("unused") // used by minecraft without us having to directly reference
    public static final LocalizationEntry TEST_BARREL_TANK_BLOCK = new LocalizationEntry(
            () -> SFMBlocks.TEST_BARREL_TANK_BLOCK.get().getDescriptionId(),
            () -> "Test Barrel Tank"
    );
    @SuppressWarnings("unused") // used by minecraft without us having to directly reference
    public static final LocalizationEntry WATER_TANK_BLOCK = new LocalizationEntry(
            () -> SFMBlocks.WATER_TANK_BLOCK.get().getDescriptionId(),
            () -> "Water Tank"
    );
    @SuppressWarnings("unused") // used by minecraft without us having to directly reference
    public static final LocalizationEntry DISK_ITEM = new LocalizationEntry(
            () -> SFMItems.DISK_ITEM.get().getDescriptionId(),
            () -> "Factory Manager Program Disk"
    );
    @SuppressWarnings("unused") // used by minecraft without us having to directly reference
    public static final LocalizationEntry EXPERIENCE_GOOP_ITEM = new LocalizationEntry(
            () -> SFMItems.EXPERIENCE_GOOP_ITEM.get().getDescriptionId(),
            () -> "Experience Goop"
    );
    @SuppressWarnings("unused") // used by minecraft without us having to directly reference
    public static final LocalizationEntry EXPERIENCE_SHARD_ITEM = new LocalizationEntry(
            () -> SFMItems.EXPERIENCE_SHARD_ITEM.get().getDescriptionId(),
            () -> "Experience Shard"
    );
    @SuppressWarnings("unused") // used by minecraft without us having to directly reference
    public static final LocalizationEntry FORM_ITEM = new LocalizationEntry(
            () -> SFMItems.FORM_ITEM.get().getDescriptionId(),
            () -> "Printing Form"
    );
    @SuppressWarnings("unused") // used by minecraft without us having to directly reference
    public static final LocalizationEntry LABEL_GUN_ITEM = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN_ITEM.get().getDescriptionId(),
            () -> "Label Gun"
    );
    @SuppressWarnings("unused") // used by minecraft without us having to directly reference
    public static final LocalizationEntry NETWORK_TOOL_ITEM = new LocalizationEntry(
            () -> SFMItems.NETWORK_TOOL_ITEM.get().getDescriptionId(),
            () -> "Network Tool"
    );
    @SuppressWarnings("unused") // used by minecraft without us having to directly reference
    public static final LocalizationEntry TEST_BARREL_BLOCK = new LocalizationEntry(
            () -> SFMBlocks.TEST_BARREL_BLOCK.get().getDescriptionId(),
            () -> "Test Barrel"
    );

    //    @SuppressWarnings("unused") // used by minecraft without us having to directly reference
//    public static final LocalizationEntry BATTERY_BLOCK = new LocalizationEntry(
//            () -> SFMBlocks.BATTERY_BLOCK.get().getDescriptionId(),
//            () -> "Battery (WIP)"
//    );
    public static final LocalizationEntry CONFIG_UPDATE_AND_SYNC_RESULT_SUCCESS = new LocalizationEntry(
            "chat.sfm.config_update_and_sync_result.success",
            "Successfully updated SFM config."
    );
    public static final LocalizationEntry CONFIG_UPDATE_AND_SYNC_RESULT_INVALID_CONFIG = new LocalizationEntry(
            "chat.sfm.config_update_and_sync_result.invalid_config",
            "The provided SFM config was invalid, no changes were made."
    );
    public static final LocalizationEntry CONFIG_UPDATE_AND_SYNC_RESULT_FAILED_TO_FIND = new LocalizationEntry(
            "chat.sfm.config_update_and_sync_result.failed_to_find",
            "Failed to find the SFM config toml."
    );
    public static final LocalizationEntry CONFIG_UPDATE_AND_SYNC_RESULT_INTERNAL_FAILURE = new LocalizationEntry(
            "chat.sfm.config_update_and_sync_result.internal_failure",
            "Something went wrong while updating the SFM config, I have no idea if changes were made. Check the server logs."
    );

    public static final LocalizationEntry PROGRAM_EDITOR_CONFIG_SCREEN_TITLE = new LocalizationEntry(
            "gui.sfm.program_editor_config.title",
            "Program Editor Config"
    );
    public static final LocalizationEntry PROGRAM_EDITOR_CONFIG_LINE_NUMBERS = new LocalizationEntry(
            "gui.sfm.program_editor_config.line_numbers",
            "Line Numbers"
    );
    public static final LocalizationEntry PROGRAM_EDITOR_CONFIG_INTELLISENSE = new LocalizationEntry(
            "gui.sfm.program_editor_config.intellisense",
            "Intellisense"
    );
    public static final LocalizationEntry
            PROGRAM_EDITOR_CONFIG_INTELLISENSE_OFF = new LocalizationEntry(
            "gui.sfm.program_editor_config.intellisense.off",
            "Off"
    );
    public static final LocalizationEntry
            PROGRAM_EDITOR_CONFIG_INTELLISENSE_BASIC = new LocalizationEntry(
            "gui.sfm.program_editor_config.intellisense.basic",
            "Basic"
    );
    public static final LocalizationEntry
            PROGRAM_EDITOR_CONFIG_INTELLISENSE_ADVANCED = new LocalizationEntry(
            "gui.sfm.program_editor_config.intellisense.advanced",
            "Advanced"
    );
    public static final LocalizationEntry PROGRAM_EDITOR_CONFIG_PREFERRED_EDITOR = new LocalizationEntry(
            "gui.sfm.program_editor_config.preferred_editor",
            "Preferred Editor"
    );
    public static final LocalizationEntry PROGRAM_EDITOR_CONFIG_PREFERRED_EDITOR_V1 = new LocalizationEntry(
            "gui.sfm.program_editor_config.preferred_editor.v1",
            "V1 (Default)"
    );
    public static final LocalizationEntry PROGRAM_EDITOR_CONFIG_PREFERRED_EDITOR_V2 = new LocalizationEntry(
            "gui.sfm.program_editor_config.preferred_editor.v2",
            "V2"
    );


    public static List<LocalizationEntry> getEntries() {
        // use reflection to get all the public static LocalizationEntry fields
        var rtn = new ArrayList<LocalizationEntry>();
        for (var field : LocalizationKeys.class.getFields()) {
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
