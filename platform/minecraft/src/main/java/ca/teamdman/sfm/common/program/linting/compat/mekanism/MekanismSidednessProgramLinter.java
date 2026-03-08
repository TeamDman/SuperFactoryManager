package ca.teamdman.sfm.common.program.linting.compat.mekanism;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.compat.SFMMekanismCompat;
import ca.teamdman.sfm.common.compat.SFMModCompat;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.program.linting.IProgramLinter;
import ca.teamdman.sfm.common.program.linting.ProblemTracker;
import ca.teamdman.sfm.common.util.SFMStreamUtils;
import ca.teamdman.sfml.ast.*;
import com.mojang.datafixers.util.Pair;
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

public class MekanismSidednessProgramLinter implements IProgramLinter {

    @SFMLocalizationDatagen
    public static final LocalizationEntry PROGRAM_WARNING_MEKANISM_USED_WITH_NULL_DIRECTION = new LocalizationEntry(
            "program.sfm.warnings.mekanism_used_without_direction",
            "Mekanism blocks are read-only from the null direction, check label \"%s\" used in \"%s\""
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry PROGRAM_WARNING_MEKANISM_BAD_SIDE_CONFIG = new LocalizationEntry(
            "program.sfm.warnings.mekanism_bad_side_config",
            "Mekanism block side config at %s doesn't agree with statement, check label \"%s\" used in \"%s\""
    );

    @Override
    public void gatherWarnings(
            Program program,
            LabelPositionHolder labels,
            @Nullable ManagerBlockEntity manager,
            ProblemTracker tracker
    ) {

        if (manager == null) return;
        Level level = manager.getLevel();
        if (level == null) return;

        // We only care about IO statements in the program
        Stream<IOStatement> ioStatements = program.getDescendantStatements()
                .filter(IOStatement.class::isInstance)
                .map(IOStatement.class::cast);
        for (IOStatement statement : SFMStreamUtils.iterate(ioStatements)) {
            if (gatherWarningsForIOStatement(statement, labels, statement, level, tracker).isSaturated()) {
                break;
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

        program.getDescendantStatements()
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
            IOStatement ioStatement,
            LabelPositionHolder labelPositionHolder,
            IOStatement statement,
            Level level,
            ProblemTracker warnings
    ) {

        SideQualifier sides = statement.labelAccess().sides();
        Stream<Pair<Label, BlockPos>> mekanismBlocks = statement
                .labelAccess()
                .getLabelledPositions(labelPositionHolder)
                .stream()
                .filter(pair -> level.isLoaded(pair.getSecond()))
                .filter(pair -> SFMModCompat.isMekanismBlock(level, pair.getSecond()));

        if (sides.sides().contains(Side.NULL)) {
            for (Pair<Label, BlockPos> pair : SFMStreamUtils.iterate(mekanismBlocks)) {
                Label label = pair.getFirst();
                if (warnings.add(PROGRAM_WARNING_MEKANISM_USED_WITH_NULL_DIRECTION.get(
                        label,
                        statement.toStringPretty()
                )).isSaturated()) {
                    return ProblemTracker.AddProblemResult.TOO_MANY_PROBLEMS;
                }
            }
        } else {
            // Check side config
            EnumSet<TransmissionType> referencedTransmissionTypes = SFMMekanismCompat
                    .getReferencedTransmissionTypes(statement);

            Predicate<DataType> dataTypePredicate;
            if (ioStatement instanceof InputStatement) {
                dataTypePredicate = dataType -> dataType.canOutput() || dataType == DataType.EXTRA;
            } else if (ioStatement instanceof OutputStatement) {
                dataTypePredicate = dataType -> dataType == DataType.INPUT
                                                || dataType == DataType.INPUT_OUTPUT
                                                || dataType == DataType.INPUT_1
                                                || dataType == DataType.INPUT_2
                                                || dataType == DataType.EXTRA;
            } else {
                throw new IllegalStateException("Unexpected value: " + ioStatement);
            }

            mekanismBlocks.forEach(pair -> {
                BlockPos blockPos = pair.getSecond();
                BlockEntity blockEntity = level.getBlockEntity(blockPos);
                if (blockEntity instanceof ISideConfiguration mekBlockEntity) {
                    TileComponentConfig config = mekBlockEntity.getConfig();
                    BlockState blockState = blockEntity.getBlockState();
                    for (TransmissionType transmissionType : referencedTransmissionTypes) {
                        boolean anySuccess = false;
                        ConfigInfo transmissionConfig = config.getConfig(transmissionType);
                        if (transmissionConfig != null) {
                            Set<Direction> activeSides = SFMMekanismCompat.getSides(transmissionConfig, mekBlockEntity, dataTypePredicate);
                            for (Direction direction : sides.resolve(blockState)) {
                                if (activeSides.contains(direction)) {
                                    anySuccess = true;
                                    break;
                                }
                            }
                        }
                        if (!anySuccess) {
                            warnings.add(PROGRAM_WARNING_MEKANISM_BAD_SIDE_CONFIG.get(
                                    blockPos,
                                    pair.getFirst(),
                                    statement.toStringPretty()
                            ));
                        }
                    }
                }
            });
        }
        return ProblemTracker.AddProblemResult.SUCCESS;
    }

    private static void fixWarningsByModifyingMekanismAccess(
            IOStatement statement,
            LabelPositionHolder labelPositionHolder,
            Level level
    ) {

        SideQualifier sides = statement.labelAccess().sides();
        Stream<Pair<Label, BlockPos>> mekanismBlocks = statement
                .labelAccess()
                .getLabelledPositions(labelPositionHolder)
                .stream()
                .filter(pair -> level.isLoaded(pair.getSecond()))
                .filter(pair -> SFMModCompat.isMekanismBlock(level, pair.getSecond()));

        // add warning if interacting with mekanism but the mekanism side config is not ALLOW
        EnumSet<TransmissionType> referencedTransmissionTypes = SFMMekanismCompat
                .getReferencedTransmissionTypes(statement);

        // Decide which DataType is correct for this statement
        Predicate<DataType> dataTypePredicate;
        DataType fixed;
        if (statement instanceof InputStatement) {
            dataTypePredicate = DataType::canOutput;
            fixed = DataType.OUTPUT; // to input from it, it must be set to output
        } else if (statement instanceof OutputStatement) {
            dataTypePredicate = dataType -> dataType == DataType.INPUT
                                            || dataType == DataType.INPUT_OUTPUT
                                            || dataType == DataType.INPUT_1
                                            || dataType == DataType.INPUT_2;
            fixed = DataType.INPUT; // to output from it, it must be set to input
        } else {
            throw new IllegalStateException("Unexpected value: " + statement);
        }

        mekanismBlocks.forEach(pair -> {
            BlockPos blockPos = pair.getSecond();
            BlockEntity blockEntity = level.getBlockEntity(blockPos);
            if (blockEntity instanceof ISideConfiguration mekBlockEntity) {
                TileComponentConfig mekBlockEntityConfig = mekBlockEntity.getConfig();
                BlockState blockState = blockEntity.getBlockState();
                Set<@Nullable Direction> directions = new HashSet<>(sides.resolve(blockState));
                for (TransmissionType transmissionType : referencedTransmissionTypes) {
                    ConfigInfo transmissionConfig = mekBlockEntityConfig.getConfig(transmissionType);
                    if (transmissionConfig != null) {
                        Set<Direction> activeSides = SFMMekanismCompat.getSides(
                                transmissionConfig,
                                mekBlockEntity,
                                dataTypePredicate
                        );
                        boolean anySuccess = directions.stream().anyMatch(activeSides::contains);
                        if (!anySuccess) {
                            // we want to enable a side for the transmission type
                            @Nullable Direction directionToEnable = sides.getNonNullDirection(blockState);
                            if (directionToEnable != null) {
                                RelativeSide relativeSide = RelativeSide.fromDirections(
                                        mekBlockEntity.getDirection(),
                                        directionToEnable
                                );
                                transmissionConfig.setDataType(fixed, relativeSide);
                                mekBlockEntityConfig.sideChanged(transmissionType, relativeSide);
                            }
                        }
                    }
                }
            }
        });
    }

}
