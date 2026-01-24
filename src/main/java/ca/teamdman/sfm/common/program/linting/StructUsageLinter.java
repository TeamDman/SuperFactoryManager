package ca.teamdman.sfm.common.program.linting;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfml.ast.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

import static ca.teamdman.sfm.common.localization.LocalizationKeys.PROGRAM_WARNING_UNUSED_STRUCT_INSTANCE;

/**
 * Linter that validates struct instances are actually used.
 */
public class StructUsageLinter implements IProgramLinter {
    @Override
    public void gatherWarnings(
            Program program,
            LabelPositionHolder labelPositionHolder,
            @Nullable ManagerBlockEntity managerBlockEntity,
            ProblemTracker tracker
    ) {
        // Collect all struct instance variable names that are used in USING clauses
        Set<String> usedInstances = new HashSet<>();
        collectUsedInstances(program, usedInstances);

        // Check for unused struct instances
        for (LetStatement letStatement : program.letStatements()) {
            String variableName = letStatement.variableName();
            if (!usedInstances.contains(variableName)) {
                if (tracker.add(PROGRAM_WARNING_UNUSED_STRUCT_INSTANCE.get(variableName)).isSaturated()) {
                    return;
                }
            }
        }
    }

    private void collectUsedInstances(ASTNode node, Set<String> usedInstances) {
        if (node instanceof LabelAccess labelAccess) {
            StructAccess structAccess = labelAccess.structAccess();
            if (structAccess != null) {
                usedInstances.add(structAccess.variableName());
            }
        }

        // Recursively check child statements
        for (Statement child : node.getStatements()) {
            collectUsedInstances(child, usedInstances);

            // Check label access in IO statements
            if (child instanceof IOStatement ioStatement) {
                LabelAccess labelAccess = ioStatement.labelAccess();
                if (labelAccess.structAccess() != null) {
                    usedInstances.add(labelAccess.structAccess().variableName());
                }
            }

            // Check label access in BoolHas expressions
            if (child instanceof IfStatement ifStatement) {
                collectUsedInstancesFromBoolExpr(ifStatement, usedInstances);
            }
        }
    }

    private void collectUsedInstancesFromBoolExpr(IfStatement ifStatement, Set<String> usedInstances) {
        // This is a simplified approach - a full implementation would need to walk
        // the entire AST tree including all BoolExpr nodes
        ifStatement.getDescendantStatements().forEach(stmt -> {
            if (stmt instanceof IOStatement ioStatement) {
                LabelAccess labelAccess = ioStatement.labelAccess();
                if (labelAccess.structAccess() != null) {
                    usedInstances.add(labelAccess.structAccess().variableName());
                }
            }
        });
    }

    @Override
    public void fixWarnings(
            Program program,
            LabelPositionHolder labels,
            ManagerBlockEntity manager,
            Level level,
            ItemStack disk
    ) {
        // We can't auto-fix this - removing unused struct instances would require
        // modifying the program source code
    }
}
