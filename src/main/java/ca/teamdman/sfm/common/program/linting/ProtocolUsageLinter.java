package ca.teamdman.sfm.common.program.linting;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfml.ast.MacroDefinition;
import ca.teamdman.sfml.ast.MacroParameter;
import ca.teamdman.sfml.ast.Program;
import ca.teamdman.sfml.ast.ProtocolDefinition;
import ca.teamdman.sfml.ast.StructDefinition;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

import static ca.teamdman.sfm.common.localization.LocalizationKeys.PROGRAM_WARNING_UNUSED_PROTOCOL;

/**
 * Linter that warns about protocols that are defined but never used.
 * A protocol is "used" if:
 * - A struct implements it
 * - A macro parameter is constrained by it
 */
public class ProtocolUsageLinter implements IProgramLinter {
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

        // Collect all protocols that are referenced
        Set<String> usedProtocols = new HashSet<>();

        // Protocols implemented by structs
        for (StructDefinition structDef : program.structDefinitions()) {
            usedProtocols.addAll(structDef.implementedProtocols());
        }

        // Protocols used as macro parameter constraints
        for (MacroDefinition macroDef : program.macroDefinitions()) {
            for (MacroParameter param : macroDef.parameters()) {
                if (param.protocolConstraint() != null) {
                    usedProtocols.add(param.protocolConstraint());
                }
            }
        }

        // Check for unused protocol definitions
        for (ProtocolDefinition protocolDef : program.protocolDefinitions()) {
            if (!usedProtocols.contains(protocolDef.name())) {
                if (tracker.add(PROGRAM_WARNING_UNUSED_PROTOCOL.get(protocolDef.name())).isSaturated()) {
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
        // Cannot auto-fix - removing unused protocols would require modifying source code
    }
}
