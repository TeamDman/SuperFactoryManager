package ca.teamdman.sfm.common;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.common.util.SFMUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class Constants {
    public static final class LocalizationKeys {
        public static final LocalizationEntry PROGRAM_EDIT_SCREEN_TITLE = new LocalizationEntry(
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
        public static final LocalizationEntry PROGRAM_EDIT_SCREEN_TOGGLE_LINE_NUMBERS_BUTTON_TOOLTIP = new LocalizationEntry(
                "gui.sfm.text_editor.toggle_line_numbers_button.tooltip",
                "Toggle line numbers"
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
        public static final LocalizationEntry PROGRAM_WARNING_RESOURCE_EACH_WITHOUT_PATTERN = new LocalizationEntry(
                "program.sfm.warnings.each_without_pattern",
                "EACH used without a pattern, statement %s"
        );

        @SuppressWarnings("unused") // used by minecraft without us having to directly reference
        public static LocalizationEntry MOD_NAME = new LocalizationEntry(
                "mod.name",
                "Super Factory Manager"
        );

        @SuppressWarnings("unused") // used by minecraft without us having to directly reference
        public static LocalizationEntry ITEM_GROUP = new LocalizationEntry(
                "itemGroup.sfm",
                "Super Factory Manager"
        );

        @SuppressWarnings("unused") // used by minecraft without us having to directly reference
        public static LocalizationEntry CABLE_BLOCK = new LocalizationEntry(
                () -> SFMBlocks.CABLE_BLOCK.get().getDescriptionId(),
                () -> "Inventory Cable"
        );

        @SuppressWarnings("unused") // used by minecraft without us having to directly reference
        public static LocalizationEntry CABLE_BLOCK_BLOCK = new LocalizationEntry(
                () -> SFMBlocks.CABLE_BLOCK_BLOCK.get().getDescriptionId(),
                () -> "Inventory Cable (Block)"
        );

        @SuppressWarnings("unused") // used by minecraft without us having to directly reference
        public static LocalizationEntry MANAGER_BLOCK = new LocalizationEntry(
                () -> SFMBlocks.MANAGER_BLOCK.get().getDescriptionId(),
                () -> "Factory Manager"
        );
        @SuppressWarnings("unused") // used by minecraft without us having to directly reference
        public static LocalizationEntry PRINTING_PRESS_BLOCK = new LocalizationEntry(
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

        @SuppressWarnings("unused") // used by minecraft without us having to directly reference
        public static LocalizationEntry TEST_BARREL_BLOCK = new LocalizationEntry(
                () -> SFMBlocks.TEST_BARREL_BLOCK.get().getDescriptionId(),
                () -> "Test Barrel"
        );

        @SuppressWarnings("unused") // used by minecraft without us having to directly reference
        public static LocalizationEntry WATER_TANK_BLOCK = new LocalizationEntry(
                () -> SFMBlocks.WATER_TANK_BLOCK.get().getDescriptionId(),
                () -> "Water Tank"
        );
        @SuppressWarnings("unused") // used by minecraft without us having to directly reference
        public static LocalizationEntry BATTERY_BLOCK = new LocalizationEntry(
                () -> SFMBlocks.BATTERY_BLOCK.get().getDescriptionId(),
                () -> "Battery (WIP)"
        );
        @SuppressWarnings("unused") // used by minecraft without us having to directly reference
        public static LocalizationEntry DISK_ITEM = new LocalizationEntry(
                () -> SFMItems.DISK_ITEM.get().getDescriptionId(),
                () -> "Factory Manager Program Disk"
        );

        @SuppressWarnings("unused") // used by minecraft without us having to directly reference
        public static LocalizationEntry EXPERIENCE_GOOP_ITEM = new LocalizationEntry(
                () -> SFMItems.EXPERIENCE_GOOP_ITEM.get().getDescriptionId(),
                () -> "Experience Goop"
        );
        @SuppressWarnings("unused") // used by minecraft without us having to directly reference
        public static LocalizationEntry EXPERIENCE_SHARD_ITEM = new LocalizationEntry(
                () -> SFMItems.EXPERIENCE_SHARD_ITEM.get().getDescriptionId(),
                () -> "Experience Shard"
        );

        @SuppressWarnings("unused") // used by minecraft without us having to directly reference
        public static LocalizationEntry FORM_ITEM = new LocalizationEntry(
                () -> SFMItems.FORM_ITEM.get().getDescriptionId(),
                () -> "Printing Form"
        );

        public static final LocalizationEntry DISK_ITEM_TOOLTIP_LABEL_HEADER = new LocalizationEntry(
                () -> SFMItems.DISK_ITEM.get().getDescriptionId() + ".tooltip.label_section.header",
                () -> "Labels"
        );
        public static final LocalizationEntry DISK_ITEM_TOOLTIP_LABEL = new LocalizationEntry(
                () -> SFMItems.DISK_ITEM.get().getDescriptionId() + ".tooltip.label_section.entry",
                () -> " - %s: %d blocks"
        );
        @SuppressWarnings("unused") // used by minecraft without us having to directly reference
        public static LocalizationEntry LABEL_GUN_ITEM = new LocalizationEntry(
                () -> SFMItems.LABEL_GUN_ITEM.get().getDescriptionId(),
                () -> "Label Gun"
        );
        public static final LocalizationEntry LABEL_GUN_CHAT_PULLED = new LocalizationEntry(
                () -> SFMItems.LABEL_GUN_ITEM.get().getDescriptionId() + ".chat.pulled",
                () -> "Pulled labels from the manager."
        );
        public static final LocalizationEntry LABEL_GUN_CHAT_PUSHED = new LocalizationEntry(
                () -> SFMItems.LABEL_GUN_ITEM.get().getDescriptionId() + ".chat.pushed",
                () -> "Pushed labels to the manager."
        );

        @SuppressWarnings("unused") // used by minecraft without us having to directly reference
        public static LocalizationEntry NETWORK_TOOL_ITEM = new LocalizationEntry(
                () -> SFMItems.NETWORK_TOOL_ITEM.get().getDescriptionId(),
                () -> "Network Tool"
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
                () -> ChatFormatting.GRAY
                      + "You might not need this, don't forget you can press "
                      + ChatFormatting.AQUA
                      + "%s"
                      + ChatFormatting.GRAY
                      + " in an inventory to toggle the inspector."
        );
        public static final LocalizationEntry LABEL_GUN_ITEM_TOOLTIP_1 = new LocalizationEntry(
                () -> SFMItems.LABEL_GUN_ITEM.get().getDescriptionId() + ".tooltip.1",
                () -> "Right click a Factory Manager to push labels."
        );
        public static final LocalizationEntry LABEL_GUN_ITEM_TOOLTIP_2 = new LocalizationEntry(
                () -> SFMItems.LABEL_GUN_ITEM.get().getDescriptionId() + ".tooltip.2",
                () -> "Right click a Factory Manager while sneaking to pull labels."
        );
        public static final LocalizationEntry LABEL_GUN_ITEM_TOOLTIP_3 = new LocalizationEntry(
                () -> SFMItems.LABEL_GUN_ITEM.get().getDescriptionId() + ".tooltip.3",
                () -> "Hold control to apply labels to blocks of the same type adjacent to cables."
        );
        public static final LocalizationEntry LABEL_GUN_ITEM_NAME_WITH_LABEL = new LocalizationEntry(
                () -> SFMItems.LABEL_GUN_ITEM.get().getDescriptionId() + ".with_label",
                () -> "Label Gun: \"%s\""
        );
        public static final LocalizationEntry WATER_TANK_ITEM_TOOLTIP_1 = new LocalizationEntry(
                () -> SFMBlocks.WATER_TANK_BLOCK.get().getDescriptionId() + ".tooltip.1",
                () -> "Requires two adjacent water sources"
        );
        public static final LocalizationEntry WATER_TANK_ITEM_TOOLTIP_2 = new LocalizationEntry(
                () -> SFMBlocks.WATER_TANK_BLOCK.get().getDescriptionId() + ".tooltip.2",
                () -> "More effective when also adjacent to other water tanks"
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
        public static final LocalizationEntry LABEL_GUN_GUI_LABEL_PLACEHOLDER = new LocalizationEntry(
                "gui.sfm.label_gun.placeholder",
                "Label"
        );
        public static final LocalizationEntry LABEL_GUN_GUI_LABEL_BUTTON = new LocalizationEntry(
                "gui.sfm.label_gun.label_button",
                "%s (%d)"
        );

        public static final LocalizationEntry LABEL_GUN_GUI_PRUNE_BUTTON = new LocalizationEntry(
                "gui.sfm.label_gun.prune_button",
                "Prune"
        );
        public static final LocalizationEntry LABEL_GUN_GUI_CLEAR_BUTTON = new LocalizationEntry(
                "gui.sfm.label_gun.clear_button",
                "Clear"
        );


        public static final LocalizationEntry MANAGER_GUI_PASTE_FROM_CLIPBOARD_BUTTON_TOOLTIP = new LocalizationEntry(
                "gui.sfm.manager.tooltip.paste",
                "Press Ctrl+V to paste."
        );
        public static final LocalizationEntry MANAGER_GUI_EDIT_BUTTON_TOOLTIP = new LocalizationEntry(
                "gui.sfm.manager.edit_button.tooltip",
                "Press Ctrl+E to edit."
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
                "!!!RESOURCE LOSS HAS OCCURRED!!! Failed to move all promised items, found %s %s:%s, took %d but had %d left over after insertion"
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
                "Cache miss - this is the first time this statement is being gathered"
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

        public static final LocalizationEntry CONTAINER_INSPECTOR_CONTAINER_SLOT_COUNT = new LocalizationEntry(
                "gui.sfm.container_inspector.container_slot_count",
                "Container Slots: %d"
        );
        public static final LocalizationEntry CONTAINER_INSPECTOR_INVENTORY_SLOT_COUNT = new LocalizationEntry(
                "gui.sfm.container_inspector.inventory_slot_count",
                "Inventory Slots: %d"
        );


        public static final LocalizationEntry MANAGER_GUI_PEAK_TICK_TIME = new LocalizationEntry(
                "gui.sfm.manager.peak_tick_time",
                "Peak tick time: %s ms"
        );
        public static final LocalizationEntry MANAGER_GUI_HOVERED_TICK_TIME = new LocalizationEntry(
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
        public static final LocalizationEntry MANAGER_GUI_REBUILD_BUTTON = new LocalizationEntry(
                "gui.sfm.manager.button.rebuild",
                "Rebuild cable network"
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
        public static final LocalizationEntry LOGS_MISSING_CAPABILITY_PROVIDER = new LocalizationEntry(
                "gui.sfm.logs.missing_capability_provider",
                "No capability provider found for %s %s direction=%s"
        );
        public static final LocalizationEntry LOGS_EMPTY_CAPABILITY = new LocalizationEntry(
                "gui.sfm.logs.empty_capability",
                "Received an empty capability %s %s direction=%s"
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
                "Cleaning up labels!"
        );

        public static final LocalizationEntry GUI_ADVANCED_TOOLTIP_HINT = new LocalizationEntry(
                "gui.sfm.advanced.tooltip.hint",
                ChatFormatting.GRAY + "Hold " + ChatFormatting.AQUA + "%s" + ChatFormatting.GRAY + " for more info"
        );

        public static final LocalizationEntry MORE_HOVER_INFO_KEY = new LocalizationEntry(
                "key.sfm.more_info",
                "Show More Item Hover Info"
        );

        public static final LocalizationEntry CONTAINER_INSPECTOR_TOGGLE_KEY = new LocalizationEntry(
                "key.sfm.container_inspector.activation_key",
                "Toggle Container Inspector"
        );

        public static final LocalizationEntry SFM_KEY_CATEGORY = new LocalizationEntry(
                "key.categories.sfm",
                "Super Factory Manager"
        );

        public static List<LocalizationEntry> getEntries() {
            // use reflection to get all the public static LocalizationEntry fields
            var rtn = new ArrayList<LocalizationEntry>();
            for (var field : Constants.LocalizationKeys.class.getFields()) {
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

        public record LocalizationEntry(
                Supplier<String> key,
                Supplier<String> value
        ) {
            public LocalizationEntry(String key, String value) {
                this(() -> key, () -> value);
            }

            public TranslatableContents get(Object... args) {
                return SFMUtils.getTranslatableContents(key.get(), args);
            }

            public TranslatableContents get() {
                return SFMUtils.getTranslatableContents(key.get());
            }

            public String getString() {
                return I18n.get(key.get());
            }

            public String getString(Object... args) {
                return I18n.get(key.get(), args);
            }

            public MutableComponent getComponent() {
                return Component.translatable(key.get());
            }

            public MutableComponent getComponent(Object... args) {
                return Component.translatable(key.get(), args);
            }
        }
    }
}
