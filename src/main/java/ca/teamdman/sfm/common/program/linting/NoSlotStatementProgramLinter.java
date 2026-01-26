package ca.teamdman.sfm.common.program.linting;

import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.SimulateExploreAllPathsProgramBehaviour;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfml.ast.IOStatement;
import ca.teamdman.sfml.ast.InputStatement;
import ca.teamdman.sfml.ast.LabelAccess;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

import static ca.teamdman.sfm.common.localization.LocalizationKeys.*;

public class NoSlotStatementProgramLinter implements IProgramLinter {
    // Check for input and output statements that gather no valid slots
    @Override
    public void gatherWarnings(
            Program program,
            LabelPositionHolder labelPositionHolder,
            @Nullable ManagerBlockEntity manager,
            ProblemTracker tracker
    ) {
        if (manager == null || manager.getLevel() == null) {
            return;
        }

        var network = CableNetworkManager.getOrRegisterNetworkFromManagerPosition(manager);
        if (network.isEmpty()) {
            return;
        }

        var simulationContext = ProgramContext.createSimulationContext(
                program,
                manager,
                network.get(),
                labelPositionHolder,
                0,
                // We won't actually be executing the program, but to be safe we use a simulation behaviour
                new SimulateExploreAllPathsProgramBehaviour()
        );
        program.getDescendantStatements()
                .filter(IOStatement.class::isInstance)
                .map(IOStatement.class::cast)
                .forEach(statement ->
                        findEmptyIOStatement(tracker, simulationContext, statement, IODirection.of(statement))
                );

    }

    private void findEmptyIOStatement(
            ProblemTracker tracker,
            ProgramContext context,
            IOStatement inputStatement,
            IODirection ioDirection
    ) {
        if (tracker.isSaturated()) {
            return;
        }

        final AtomicBoolean anyCapability = new AtomicBoolean(false);
        final AtomicBoolean anyMatches = new AtomicBoolean(false);
        final AtomicBoolean ioDirectionMatches = new AtomicBoolean(false);
        for (var resourceType : inputStatement.resourceLimits().getReferencedResourceTypes()) {
            resourceType.forEachCapability(
                    context,
                    inputStatement.labelAccess(),
                    (label, pos, direction, cap) -> {
                        anyCapability.set(true);
                        //noinspection rawtypes,unchecked
                        searchForValidSlots(
                                (ResourceType) resourceType,
                                inputStatement.labelAccess(),
                                cap,
                                ioDirection,
                                anyMatches,
                                ioDirectionMatches
                        );
                    }

            );
        }

        // Checking for missing capabilities is currently the responsibility of LabelNotConnectedProgramLinter
        if (anyCapability.get()) {
            LocalizationEntry warning = null;
            if (!anyMatches.get()) {
                warning = PROGRAM_WARNING_NO_SLOTS;
            } else if (!ioDirectionMatches.get()) {
                warning = switch (ioDirection) {
                    case INPUT -> PROGRAM_WARNING_NO_VIABLE_INPUT_SLOTS;
                    case OUTPUT -> PROGRAM_WARNING_NO_VIABLE_OUTPUT_SLOTS;
                };
            }
            if (warning != null) {
                tracker.add(warning.get(
                        inputStatement,
                        context.getProgram().astBuilder().getLineColumnForNode(inputStatement)
                ));
            }
        }
    }

    private <STACK, ITEM, CAP> void searchForValidSlots(
            ResourceType<STACK, ITEM, CAP> type,
            LabelAccess labelAccess,
            CAP capability,
            IODirection ioDirection,
            AtomicBoolean anyMatches,
            AtomicBoolean ioDirectionMatches
    ) {
        for (int slot = 0; slot < type.getSlots(capability); slot++) {
            if (labelAccess.slots().contains(slot)) {
                anyMatches.set(true);
                boolean canPerformDirection = switch (ioDirection) {
                    case INPUT -> type.canExtract(capability, slot);
                    case OUTPUT -> type.canInsert(capability, slot);
                };
                if (canPerformDirection) {
                    ioDirectionMatches.set(true);
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
    }


    enum IODirection {
        INPUT,
        OUTPUT;

        public static IODirection of(IOStatement statement) {
            return statement instanceof InputStatement ? IODirection.INPUT : IODirection.OUTPUT;
        }
    }
}
