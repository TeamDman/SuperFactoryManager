package ca.teamdman.sfm.common.program.linting;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.compat.SFMMekanismCompat;
import ca.teamdman.sfm.common.compat.SFMModCompat;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfml.ast.*;
import ca.teamdman.sfm.common.util.Pair;
import mekanism.api.RelativeSide;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.interfaces.ISideConfiguration;
import net.minecraft.util.math.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static ca.teamdman.sfm.common.localization.LocalizationKeys.PROGRAM_WARNING_MEKANISM_BAD_SIDE_CONFIG;
import static ca.teamdman.sfm.common.localization.LocalizationKeys.PROGRAM_WARNING_MEKANISM_USED_WITHOUT_DIRECTION;

public class MekanismSideConfigProgramLinter implements IProgramLinter {

    @Override
    public ArrayList<TextComponentTranslation> gatherWarnings(
            Program program,
            LabelPositionHolder labelPositionHolder,
            @Nullable ManagerBlockEntity managerBlockEntity
    ) {
        ArrayList<TextComponentTranslation> warnings = new ArrayList<>();

        if (!SFMModCompat.isMekanismLoaded()) {
            return warnings;
        }
        if (managerBlockEntity == null) {
            return warnings;
        }

        Level level = managerBlockEntity.getLevel();
        if (level == null) {
            return warnings;
        }

        // We only care about IO statements in the program
        program.getDescendantStatements()
                .filter(IOStatement.class::isInstance)
                .map(IOStatement.class::cast)
                .forEach(statement ->
                                 addWarningsForMekanismAccess(statement, labelPositionHolder, statement, level, warnings)
                );

        return warnings;
    }

    @Override
    public void fixWarnings(
            ManagerBlockEntity managerBlockEntity,
            ItemStack diskStack,
            Program program
    ) {
        if (!SFMModCompat.isMekanismLoaded()) return;
        if (managerBlockEntity == null || managerBlockEntity.getLevel() == null) {
            return;
        }
        Level level = managerBlockEntity.getLevel();

        program.getDescendantStatements()
                .filter(IOStatement.class::isInstance)
                .map(IOStatement.class::cast)
                .forEach(statement ->
                                 fixWarningsByModifyingMekanismAccess(statement, LabelPositionHolder.from(diskStack), level)
                );
    }

    // ------------------------------------------
    // PRIVATE METHODS
    // ------------------------------------------

    private static void addWarningsForMekanismAccess(
            IOStatement ioStatement,
            LabelPositionHolder labelPositionHolder,
            IOStatement statement,
            Level level,
            ArrayList<TextComponentTranslation> warnings
    ) {
        if (!SFMModCompat.isMekanismLoaded()) return;

        DirectionQualifier directions = statement.labelAccess().directions();
        Stream<Pair<Label, BlockPos>> mekanismBlocks = statement
                .labelAccess()
                .getLabelledPositions(labelPositionHolder)
                .stream()
                .filter(pair -> level.isLoaded(pair.getSecond()))
                .filter(pair -> SFMModCompat.isMekanismBlock(level, pair.getSecond()));

        if (directions.equals(DirectionQualifier.NULL_DIRECTION)) {
            // No side specified
            mekanismBlocks.forEach(pair ->
                                           warnings.add(PROGRAM_WARNING_MEKANISM_USED_WITHOUT_DIRECTION.get(
                                                   pair.getFirst(),
                                                   statement.toStringPretty()
                                           ))
            );
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
                if (level.getBlockEntity(blockPos) instanceof ISideConfiguration mekBlockEntity) {
                    TileComponentConfig config = mekBlockEntity.getConfig();
                    for (TransmissionType transmissionType : referencedTransmissionTypes) {
                        boolean anySuccess = false;
                        ConfigInfo transmissionConfig = config.getConfig(transmissionType);
                        if (transmissionConfig != null) {
                            Set<Direction> activeSides = transmissionConfig.getSides(dataTypePredicate);
                            for (Direction direction : directions) {
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
    }

    private static void fixWarningsByModifyingMekanismAccess(
            IOStatement statement,
            LabelPositionHolder labelPositionHolder,
            Level level
    ) {
        DirectionQualifier directions = statement.labelAccess().directions();
        Stream<Pair<Label, BlockPos>> mekanismBlocks =
                statement.labelAccess()
                        .getLabelledPositions(labelPositionHolder)
                        .stream()
                        .filter(pair -> SFMModCompat.isMekanismBlock(level, pair.getSecond()));

        EnumSet<TransmissionType> referencedTransmissionTypes = SFMMekanismCompat
                .getReferencedTransmissionTypes(statement);

        // Decide which DataType is correct for this statement
        Predicate<DataType> dataTypePredicate;
        DataType fixed;
        if (statement instanceof InputStatement) {
            dataTypePredicate = DataType::canOutput;
            fixed = DataType.OUTPUT;
        } else if (statement instanceof OutputStatement) {
            dataTypePredicate = dataType -> dataType == DataType.INPUT
                                            || dataType == DataType.INPUT_OUTPUT
                                            || dataType == DataType.INPUT_1
                                            || dataType == DataType.INPUT_2;
            fixed = DataType.INPUT;
        } else {
            throw new IllegalStateException("Unexpected value: " + statement);
        }

        mekanismBlocks.forEach(pair -> {
            BlockPos blockPos = pair.getSecond();
            if (level.getBlockEntity(blockPos) instanceof ISideConfiguration mekBlockEntity) {
                TileComponentConfig mekBlockEntityConfig = mekBlockEntity.getConfig();
                for (TransmissionType transmissionType : referencedTransmissionTypes) {
                    ConfigInfo transmissionConfig = mekBlockEntityConfig.getConfig(transmissionType);
                    if (transmissionConfig != null) {
                        Set<Direction> activeSides = transmissionConfig.getSides(dataTypePredicate);
                        boolean anySuccess = directions.stream().anyMatch(activeSides::contains);
                        if (!anySuccess) {
                            // pick the first direction from the statement
                            Direction statementSide = directions.iterator().next();
                            if (statementSide != null) {
                                RelativeSide relativeSide = RelativeSide.fromDirections(
                                        mekBlockEntity.getDirection(),
                                        statementSide
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
