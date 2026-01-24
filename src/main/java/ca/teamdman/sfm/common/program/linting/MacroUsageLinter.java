package ca.teamdman.sfm.common.program.linting;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfml.ast.ExpandStatement;
import ca.teamdman.sfml.ast.MacroDefinition;
import ca.teamdman.sfml.ast.Program;
import ca.teamdman.sfml.ast.Statement;
import ca.teamdman.sfml.ast.Trigger;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import static ca.teamdman.sfm.common.localization.LocalizationKeys.PROGRAM_WARNING_UNUSED_MACRO;

/**
 * Linter that validates macro usage.
 * Warns if a macro is defined but never expanded.
 */
public class MacroUsageLinter implements IProgramLinter {
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

        // Collect all macro names that are used in expand statements
        Set<String> usedMacros = new HashSet<>();
        collectUsedMacros(program, usedMacros);

        // Check for unused macro definitions
        for (MacroDefinition macroDef : program.macroDefinitions()) {
            if (!usedMacros.contains(macroDef.name())) {
                if (tracker.add(PROGRAM_WARNING_UNUSED_MACRO.get(macroDef.name())).isSaturated()) {
                    return;
                }
            }
        }
    }

    private void collectUsedMacros(Program program, Set<String> usedMacros) {
        Deque<Statement> toVisit = new ArrayDeque<>();
        for (Trigger trigger : program.triggers()) {
            toVisit.addAll(trigger.getStatements());
        }

        while (!toVisit.isEmpty()) {
            Statement stmt = toVisit.poll();

            if (stmt instanceof ExpandStatement expand) {
                usedMacros.add(expand.macroName());
            }

            toVisit.addAll(stmt.getStatements());
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
        // Cannot auto-fix - removing unused macros would require modifying source code
    }
}
