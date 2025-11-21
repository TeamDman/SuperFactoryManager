package ca.teamdman.sfm.common.program.linting;

import static ca.teamdman.sfm.common.localization.LocalizationKeys.*;
import static ca.teamdman.sfml.ast.RoundRobin.Behaviour.BY_BLOCK;
import static ca.teamdman.sfml.ast.RoundRobin.Behaviour.BY_LABEL;

import java.util.ArrayList;

import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.registries.IForgeRegistryEntry;

import org.jetbrains.annotations.Nullable;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfml.ast.IOStatement;
import ca.teamdman.sfml.ast.Program;
import ca.teamdman.sfml.ast.ResourceQuantity;
import ca.teamdman.sfml.ast.RoundRobin;

public class FlowProgramLinter extends IForgeRegistryEntry.Impl<IProgramLinter> implements IProgramLinter {

    @Override
    public ArrayList<TextComponentTranslation> gatherWarnings(
                                                              Program program,
                                                              LabelPositionHolder labelPositionHolder,
                                                              @Nullable ManagerBlockEntity managerBlockEntity) {
        ArrayList<TextComponentTranslation> warnings = new ArrayList<>();

        // 1) Ensure we have both input and output if needed
        addWarningsForUsingIOWithoutCorrespondingOppositeIO(program, labelPositionHolder, warnings);

        // 2) For each IO statement, check "each" usage and round-robin usage
        program.getDescendantStatements()
                .filter(IOStatement.class::isInstance)
                .map(IOStatement.class::cast)
                .forEach(statement -> {
                    addWarningsForSmellyRoundRobinUsage(warnings, statement);
                    addWarningsForUsingEachWithoutAPattern(warnings, statement);
                });

        return warnings;
    }

    @Override
    public void fixWarnings(
                            ManagerBlockEntity managerBlockEntity,
                            ItemStack diskStack,
                            Program program) {
        // Typically, these warnings can’t be “auto-fixed.”
    }

    // ------------------------------------------
    // PRIVATE METHODS
    // ------------------------------------------

    private void addWarningsForUsingIOWithoutCorrespondingOppositeIO(
                                                                     Program program,
                                                                     LabelPositionHolder labelPositionHolder,
                                                                     ArrayList<TextComponentTranslation> warnings) {
        program.tick(
                ProgramContext.createSimulationContext(
                        program,
                        labelPositionHolder,
                        0,
                        new GatherWarningsProgramBehaviour(warnings::addAll)));
    }

    private void addWarningsForUsingEachWithoutAPattern(
                                                        ArrayList<TextComponentTranslation> warnings,
                                                        IOStatement statement) {
        boolean smells = statement
                .resourceLimits()
                .resourceLimitList()
                .stream()
                .anyMatch(rl -> rl.limit().quantity().idExpansionBehaviour() ==
                        ResourceQuantity.IdExpansionBehaviour.EXPAND && !rl.resourceIds().couldMatchMoreThanOne());
        if (smells) {
            warnings.add(PROGRAM_WARNING_RESOURCE_EACH_WITHOUT_PATTERN.get(statement.toStringPretty()));
        }
    }

    private void addWarningsForSmellyRoundRobinUsage(
                                                     ArrayList<TextComponentTranslation> warnings,
                                                     IOStatement statement) {
        RoundRobin roundRobin = statement.labelAccess().roundRobin();
        if (roundRobin.getBehaviour() == BY_BLOCK && statement.each()) {
            warnings.add(PROGRAM_WARNING_ROUND_ROBIN_SMELLY_EACH.get(statement.toStringPretty()));
        } else if (roundRobin.getBehaviour() == BY_LABEL && statement.labelAccess().labels().size() == 1) {
            warnings.add(PROGRAM_WARNING_ROUND_ROBIN_SMELLY_COUNT.get(statement.toStringPretty()));
        }
    }
}
