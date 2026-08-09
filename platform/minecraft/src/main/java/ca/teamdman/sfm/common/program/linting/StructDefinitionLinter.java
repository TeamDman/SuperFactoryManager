package ca.teamdman.sfm.common.program.linting;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfml.ast.LetStatement;
import ca.teamdman.sfml.ast.Program;
import ca.teamdman.sfml.ast.StructDefinition;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Linter that validates struct definitions are actually used.
 */
public class StructDefinitionLinter implements IProgramLinter {
    @SFMLocalizationDatagen
    public static final LocalizationEntry PROGRAM_WARNING_UNUSED_STRUCT = new LocalizationEntry(
            "program.sfm.warnings.unused_struct",
            "Struct \"%s\" is defined but never instantiated."
    );

    @Override
    public void gatherWarnings(
            Program program,
            LabelPositionHolder labelPositionHolder,
            @Nullable ManagerBlockEntity managerBlockEntity,
            ProblemTracker tracker
    ) {
        // Skip for library disks (no triggers = definitions are meant to be exported)
        if (program.triggers().isEmpty()) {
            return;
        }

        // Collect all struct names that are instantiated
        Set<String> usedStructs = new HashSet<>();
        for (LetStatement letStatement : program.letStatements()) {
            usedStructs.add(letStatement.instance().definition().name());
        }

        // Check for unused struct definitions
        for (StructDefinition structDef : program.structDefinitions()) {
            if (!usedStructs.contains(structDef.name())) {
                if (tracker.add(PROGRAM_WARNING_UNUSED_STRUCT.get(structDef.name())).isSaturated()) {
                    return;
                }
            }
        }
    }

    @Override
    public void fixWarnings(
            Program program,
            LabelPositionHolder labels,
            ManagerBlockEntity manager,
            Level level,
            ItemStack disk
    ) {
        // We can't auto-fix this - removing unused struct definitions would require
        // modifying the program source code
    }
}
