package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;

public interface IOStatement extends Statement, ToStringPretty {
    @SFMLocalizationDatagen
    LocalizationEntry LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_FOR_RESOURCE_TYPE = new LocalizationEntry(
            "log.sfm.statement.tick.io.gather_slots.resource_types",
            "Gathering for: %s (%s)"
    );

    @SFMLocalizationDatagen
    LocalizationEntry LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_EACH = new LocalizationEntry(
            "log.sfm.statement.tick.io.gather_slots.each",
            "EACH keyword used - trackers will be unique to each block"
    );

    @SFMLocalizationDatagen
    LocalizationEntry LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_NOT_EACH = new LocalizationEntry(
            "log.sfm.statement.tick.io.gather_slots.not_each",
            "EACH keyword not used - trackers will be shared between blocks"
    );

    @SFMLocalizationDatagen
    LocalizationEntry LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_RANGE = new LocalizationEntry(
            "log.sfm.statement.tick.io.gather_slots.range",
            "Gathering slots in range set: %s"
    );

    @SFMLocalizationDatagen
    LocalizationEntry LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_SLOT_NOT_IN_RANGE = new LocalizationEntry(
            "log.sfm.statement.tick.io.gather_slots.not_in_range",
            "Slot %d - not in range"
    );

    @SFMLocalizationDatagen
    LocalizationEntry LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_SLOT_SHOULD_NOT_CREATE = new LocalizationEntry(
            "log.sfm.statement.tick.io.gather_slots.should_not_create",
            "Slot %d - skipping - %s"
    );

    @SFMLocalizationDatagen
    LocalizationEntry LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_SLOT_CREATED = new LocalizationEntry(
            "log.sfm.statement.tick.io.gather_slots.created",
            "Slot %d - tracking - %s - %s"
    );

    @SFMLocalizationDatagen
    LocalizationEntry LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS = new LocalizationEntry(
            "log.sfm.statement.tick.io.gather_slots",
            "Gathering slots for IO statement \n```\n%s\n```\n"
    );

    LabelAccess labelAccess();

    ResourceLimits resourceLimits();

    boolean each();

}
