package ca.teamdman.sfm.common.program.linting;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfml.ast.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Linter that validates definitions (protocols, macros, struct instances) are actually used.
 * Consolidates checks for:
 * - Unused protocol definitions
 * - Unused macro definitions
 * - Unused struct instances (let statements)
 */
public class UnusedDefinitionLinter implements IProgramLinter {
    @SFMLocalizationDatagen
    public static final LocalizationEntry PROGRAM_WARNING_UNUSED_STRUCT_INSTANCE = new LocalizationEntry(
            "program.sfm.warnings.unused_struct_instance",
            "Struct instance \"%s\" is defined but never used in a USING clause."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry PROGRAM_WARNING_UNUSED_PROTOCOL = new LocalizationEntry(
            "program.sfm.warnings.unused_protocol",
            "Protocol \"%s\" is defined but never used."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry PROGRAM_WARNING_UNUSED_MACRO = new LocalizationEntry(
            "program.sfm.warnings.unused_macro",
            "Macro \"%s\" is defined but never expanded."
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

        checkUnusedProtocols(program, tracker);
        if (tracker.isSaturated()) return;

        checkUnusedMacros(program, tracker);
        if (tracker.isSaturated()) return;

        checkUnusedStructInstances(program, tracker);
    }

    private void checkUnusedProtocols(Program program, ProblemTracker tracker) {
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

    private void checkUnusedMacros(Program program, ProblemTracker tracker) {
        // Collect all macro names that are used in expand statements
        Set<String> usedMacros = new HashSet<>();

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

        // Check for unused macro definitions
        for (MacroDefinition macroDef : program.macroDefinitions()) {
            if (!usedMacros.contains(macroDef.name())) {
                if (tracker.add(PROGRAM_WARNING_UNUSED_MACRO.get(macroDef.name())).isSaturated()) {
                    return;
                }
            }
        }
    }

    private void checkUnusedStructInstances(Program program, ProblemTracker tracker) {
        // Collect all struct instance variable names that are used
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
        // Walk the entire AST tree including all BoolExpr nodes
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
        // Cannot auto-fix - removing unused definitions would require modifying source code
    }
}
