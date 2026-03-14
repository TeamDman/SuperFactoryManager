package ca.teamdman.sfm.common.program.linting;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.cablenetwork.CableNetwork;
import ca.teamdman.sfm.common.cablenetwork.CableNetworkManager;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.logging.TranslatableLogger;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfml.ast.IOStatement;
import ca.teamdman.sfml.ast.InputStatement;
import ca.teamdman.sfml.ast.ResourceAccess;
import ca.teamdman.sfml.ast.SFMLProgram;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

import static ca.teamdman.sfm.common.localization.LocalizationKeys.*;

/// Evaluate {@link IOStatement} where the {@link ResourceAccess} has bad slots.
/// Bad slots determined via {@link ResourceType#canExtract(Object, int)} and {@link ResourceType#canInsert(Object, int)} responses.
public class NoEligibleSlotsProgramLinter implements IProgramLinter {

    @Override
    public void gatherWarnings(
            SFMLProgram program,
            LabelPositionHolder labelPositionHolder,
            @Nullable ManagerBlockEntity manager,
            ProblemTracker tracker
    ) {

        if (manager == null || manager.getLevel() == null) {
            return;
        }

        var network = CableNetworkManager.getOrRegisterNetworkFromManagerPosition(manager).orElse(null);
        if (network == null) {
            return;
        }

        program.getDescendantNodes()
                .filter(IOStatement.class::isInstance)
                .map(IOStatement.class::cast)
                .forEach(statement ->
                                 findEmptyIOStatement(
                                         program,
                                         manager.logger,
                                         manager.getLevel(),
                                         network,
                                         labelPositionHolder,
                                         tracker,
                                         statement,
                                         IODirection.of(statement)
                                 )
                );

    }

    @Override
    public void fixWarnings(
            SFMLProgram program,
            LabelPositionHolder labels,
            ManagerBlockEntity manager,
            Level level,
            ItemStack disk
    ) {

    }

    private void findEmptyIOStatement(
            SFMLProgram program,
            TranslatableLogger logger,
            Level level,
            CableNetwork network,
            LabelPositionHolder labelPositionHolder,
            ProblemTracker tracker,
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
            ResourceAccess resourceAccess = inputStatement.resourceAccess();
            resourceType.forEachCapability(
                    logger,
                    level,
                    network,
                    labelPositionHolder,
                    resourceAccess,
                    (labelExpression, pos, direction, cap) -> {
                        anyCapability.set(true);
                        searchForValidSlots(
                                (ResourceType<Object, Object, Object>) resourceType,
                                inputStatement.resourceAccess(),
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
                        program.astBuilder().getLineColumnForNode(inputStatement)
                ));
            }
        }
    }

    private <STACK, ITEM, CAP> void searchForValidSlots(
            ResourceType<STACK, ITEM, CAP> type,
            ResourceAccess resourceAccess,
            CAP capability,
            IODirection ioDirection,
            AtomicBoolean anyMatches,
            AtomicBoolean ioDirectionMatches
    ) {

        for (int slot = 0; slot < type.getSlots(capability); slot++) {
            if (resourceAccess.slots().contains(slot)) {
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


    enum IODirection {
        INPUT,
        OUTPUT;

        public static IODirection of(IOStatement statement) {

            return statement instanceof InputStatement ? IODirection.INPUT : IODirection.OUTPUT;
        }
    }

}
