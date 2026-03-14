package ca.teamdman.sfm.common.program.linting.compat.mekanism;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.compat.SFMMekanismCompat;
import ca.teamdman.sfm.common.compat.SFMModCompat;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.program.linting.IProgramLinter;
import ca.teamdman.sfm.common.program.linting.ProblemTracker;
import ca.teamdman.sfm.common.util.SFMStreamUtils;
import ca.teamdman.sfml.ast.*;
import mekanism.api.RelativeSide;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.interfaces.ISideConfiguration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static ca.teamdman.sfm.common.localization.LocalizationKeys.PROGRAM_WARNING_MEKANISM_BAD_SIDE_CONFIG;
import static ca.teamdman.sfm.common.localization.LocalizationKeys.PROGRAM_WARNING_MEKANISM_USED_WITH_NULL_DIRECTION;

public class MekanismSidednessProgramLinter implements IProgramLinter {

    @Override
    public void gatherWarnings(
            SFMLProgram program,
            LabelPositionHolder labels,
            @Nullable ManagerBlockEntity manager,
            ProblemTracker tracker
    ) {

        if (manager == null) return;
        Level level = manager.getLevel();
        if (level == null) return;

        // We only care about IO statements in the program
        Stream<IOStatement> ioStatements = program.getDescendantNodes()
                .filter(IOStatement.class::isInstance)
                .map(IOStatement.class::cast);
        for (IOStatement statement : SFMStreamUtils.iterate(ioStatements)) {
            if (gatherWarningsForIOStatement(program, labels, statement, level, tracker).isSaturated()) {
                break;
            }
        }
    }

    @Override
    public void fixWarnings(
            SFMLProgram program,
            LabelPositionHolder labels,
            ManagerBlockEntity manager,
            Level level,
            ItemStack disk
    ) {

        program.getDescendantNodes()
                .filter(IOStatement.class::isInstance)
                .map(IOStatement.class::cast)
                .forEach(statement ->
                                 fixWarningsByModifyingMekanismAccess(
                                         statement,
                                         LabelPositionHolder.from(disk),
                                         level
                                 )
                );
    }

    private static ProblemTracker.AddProblemResult gatherWarningsForIOStatement(
            SFMLProgram program,
            LabelPositionHolder labelPositionHolder,
            IOStatement ioStatement,
            Level level,
            ProblemTracker warnings
    ) {
        // Identify which sides the program expects to succeed
        SideQualifier sides = ioStatement.resourceAccess().sides();

        // Identify if the null side is used
        boolean nullSideUsed = sides.sides().contains(Side.NULL);

        // For each label expression, check the positions
        for (LabelExpression labelExpression : ioStatement.resourceAccess().labelExpressions()) {
            Set<BlockPos> positions = labelExpression.getPositions(labelPositionHolder);
            for (BlockPos pos : positions) {
                if (!level.isLoaded(pos)) {
                    continue;
                }
                if (!SFMModCompat.isMekanismBlock(level, pos)) {
                    continue;
                }
                if (nullSideUsed) {
                    // Warn the user that NULL SIDE is read-only
                    ProblemTracker.AddProblemResult result = warnings.add(
                            PROGRAM_WARNING_MEKANISM_USED_WITH_NULL_DIRECTION.get(
                                    labelExpression,
                                    ioStatement.toStringPretty() + " at " + program
                                            .astBuilder()
                                            .getLineColumnForNode(ioStatement)
                            )
                    );
                    if (result.isSaturated()) {
                        return ProblemTracker.AddProblemResult.TOO_MANY_PROBLEMS;
                    }
                } else {
                    // Check if the side config aligns with the program's expectations

                    // Get the block entity
                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    if (!(blockEntity instanceof ISideConfiguration mekBlockEntity)) {
                        continue;
                    }

                    // Get the block state
                    BlockState blockState = blockEntity.getBlockState();

                    // Determine which transmission types the statement is using
                    EnumSet<TransmissionType> referencedTransmissionTypes
                            = SFMMekanismCompat.getReferencedTransmissionTypes(ioStatement);

                    // Get the current side config
                    TileComponentConfig config = mekBlockEntity.getConfig();

                    // Create a matcher for the input, output, extra mekanism slots
                    Predicate<DataType> expectedDataTypePredicate = getDataTypePredicate(ioStatement);


                    // Get the directions that the program is using
                    Set<@Nullable Direction> directions = new HashSet<>(sides.resolve(blockState));

                    // For each of the transmission types, ensure one of the program sides matches the mek config
                    for (TransmissionType transmissionType : referencedTransmissionTypes) {
                        boolean mekAndProgramSideConfigAgree = false;

                        // Determine the sides active for that transmission type
                        ConfigInfo transmissionConfig = config.getConfig(transmissionType);
                        if (transmissionConfig == null) {
                            continue;
                        }
                        Set<Direction> activeSides = SFMMekanismCompat.getSides(
                                transmissionConfig,
                                mekBlockEntity,
                                expectedDataTypePredicate
                        );

                        // Resolve the relative sides to absolute directions
                        for (Direction direction : directions) {
                            if (activeSides.contains(direction)) {
                                mekAndProgramSideConfigAgree = true;
                                break;
                            }
                        }

                        // Only warn if no sides agreed for this transmission type
                        if (!mekAndProgramSideConfigAgree) {
                            warnings.add(PROGRAM_WARNING_MEKANISM_BAD_SIDE_CONFIG.get(
                                    pos,
                                    labelExpression,
                                    ioStatement.toStringPretty() + " at " + program
                                            .astBuilder()
                                            .getLineColumnForNode(ioStatement)
                            ));
                        }
                    }

                }

            }
        }
        return ProblemTracker.AddProblemResult.SUCCESS;
    }

    private static Predicate<DataType> getDataTypePredicate(IOStatement ioStatement) {

        if (ioStatement instanceof InputStatement) {
            return dataType -> dataType.canOutput() || dataType == DataType.EXTRA;
        } else if (ioStatement instanceof OutputStatement) {
            return dataType -> dataType == DataType.INPUT
                               || dataType == DataType.INPUT_OUTPUT
                               || dataType == DataType.INPUT_1
                               || dataType == DataType.INPUT_2
                               || dataType == DataType.EXTRA;
        } else {
            throw new IllegalStateException("Unexpected value: " + ioStatement);
        }
    }

    private static void fixWarningsByModifyingMekanismAccess(
            IOStatement ioStatement,
            LabelPositionHolder labelPositionHolder,
            Level level
    ) {

        SideQualifier sides = ioStatement.resourceAccess().sides();

        // Determine which transmission types the statement is using
        EnumSet<TransmissionType> referencedTransmissionTypes
                = SFMMekanismCompat.getReferencedTransmissionTypes(ioStatement);

        // Determine the correct DataType for the side to be configured as
        Predicate<DataType> expectedDataTypePredicate;
        DataType newDataType;
        if (ioStatement instanceof InputStatement) {
            // to input from it, it must be set to output
            expectedDataTypePredicate = DataType::canOutput;
            newDataType = DataType.OUTPUT;
        } else if (ioStatement instanceof OutputStatement) {
            // to output from it, it must be set to input
            expectedDataTypePredicate = dataType -> dataType == DataType.INPUT
                                            || dataType == DataType.INPUT_OUTPUT
                                            || dataType == DataType.INPUT_1
                                            || dataType == DataType.INPUT_2;
            newDataType = DataType.INPUT;
        } else {
            throw new IllegalStateException("Unexpected value: " + ioStatement);
        }


        // For each label expression, check the positions
        for (LabelExpression labelExpression : ioStatement.resourceAccess().labelExpressions()) {
            Set<BlockPos> positions = labelExpression.getPositions(labelPositionHolder);
            for (BlockPos pos : positions) {
                // Position must be loaded
                if (!level.isLoaded(pos)) {
                    continue;
                }

                // Block entity must support ISideConfiguration from Mekanism
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (!(blockEntity instanceof ISideConfiguration mekBlockEntity)) {
                    continue;
                }

                // Get the block state
                BlockState blockState = blockEntity.getBlockState();

                // Get the current side config
                TileComponentConfig mekBlockEntityConfig = mekBlockEntity.getConfig();

                // Get the directions that the program is using
                Set<@Nullable Direction> directions = new HashSet<>(sides.resolve(blockState));

                // For each of the transmission types, ensure one of the program sides matches the mek config
                for (TransmissionType transmissionType : referencedTransmissionTypes) {

                    // Determine the sides active for that transmission type
                    ConfigInfo transmissionConfig = mekBlockEntityConfig.getConfig(transmissionType);
                    if (transmissionConfig == null) {
                        continue;
                    }
                    Set<Direction> activeSides = SFMMekanismCompat.getSides(
                            transmissionConfig,
                            mekBlockEntity,
                            expectedDataTypePredicate
                    );

                    // Determine if a fix-up is necessary
                    if (directions.stream().anyMatch(activeSides::contains)) {
                        continue;
                    }

                    // Determine which direction to enable
                    @Nullable Direction directionToEnable = null;
                    for (Direction direction : directions) {
                        if (direction != null) {
                            directionToEnable = direction;
                            break;
                        }
                    }
                    if (directionToEnable == null) {
                        continue;
                    }

                    // Convert the direction to a relative side
                    RelativeSide relativeSide = RelativeSide.fromDirections(
                            mekBlockEntity.getDirection(),
                            directionToEnable
                    );

                    // Enable the data type to flow through that side
                    transmissionConfig.setDataType(newDataType, relativeSide);

                    // Mark the side config as having changed
                    mekBlockEntityConfig.sideChanged(transmissionType, relativeSide);
                }

            }
        }

    }

}
