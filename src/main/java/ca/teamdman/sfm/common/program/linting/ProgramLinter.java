package ca.teamdman.sfm.common.program.linting;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.cablenetwork.CableNetworkManager;
import ca.teamdman.sfm.common.compat.SFMMekanismCompat;
import ca.teamdman.sfm.common.compat.SFMModCompat;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.program.LabelPositionHolder;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.registry.SFMBlockCapabilities;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
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
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ca.teamdman.sfm.common.localization.LocalizationKeys.*;
import static ca.teamdman.sfml.ast.RoundRobin.Behaviour.BY_BLOCK;
import static ca.teamdman.sfml.ast.RoundRobin.Behaviour.BY_LABEL;

public class ProgramLinter {
    @SuppressWarnings("ConstantValue")
    public static ArrayList<TranslatableContents> gatherWarnings(
            Program program,
            LabelPositionHolder labelPositionHolder,
            @Nullable ManagerBlockEntity manager
    ) {
        var warnings = new ArrayList<TranslatableContents>();
        var level = manager != null ? manager.getLevel() : null;

        // label smells
        int before = warnings.size();
        addWarningsForLabelsInProgramButNotInHolder(program, labelPositionHolder, warnings);
        addWarningsForLabelsInHolderButNotInProgram(program, labelPositionHolder, warnings);
        if (level != null) {
            addWarningsForLabelsUsedInWorldButNotConnectedByCables(manager, labelPositionHolder, warnings, level);
        }
        int after = warnings.size();
        if (before != after) {
            // add reminder to push labels
            warnings.add(PROGRAM_REMINDER_PUSH_LABELS.get());
        }

        // logical flow smells
        addWarningsForUsingIOWithoutCorrespondingOppositeIO(program, labelPositionHolder, warnings);

        // resource smells
        addWarningsForResourcesReferencedButNotFoundInRegistry(program, warnings);

        // simple io statement smells
        program
                .getDescendantStatements()
                .filter(IOStatement.class::isInstance)
                .map(IOStatement.class::cast)
                .forEach(statement -> {
                    addWarningsForSmellyRoundRobinUsage(warnings, statement);
                    addWarningsForUsingEachWithoutAPattern(warnings, statement);
                    if (level != null) {
                        addWarningsForSmellyMekanismAccess(statement, labelPositionHolder, statement, level, warnings);
                    }

                });

        return warnings;
    }

    public static void fixWarnings(
            ManagerBlockEntity manager,
            ItemStack disk,
            Program program
    ) {
        fixWarningsByRemovingBadLabelsFromDisk(manager, disk, program);
        LabelPositionHolder labelPositionHolder = LabelPositionHolder.from(disk);
        Level level = manager.getLevel();
        if (level != null) {
            program
                    .getDescendantStatements()
                    .filter(IOStatement.class::isInstance)
                    .map(IOStatement.class::cast)
                    .forEach(statement -> fixWarningsByModifyingMekanismAccess(statement, labelPositionHolder, level));
        }
        manager.rebuildProgramAndUpdateDisk();
    }

    private static void fixWarningsByRemovingBadLabelsFromDisk(
            ManagerBlockEntity manager,
            ItemStack disk,
            Program program
    ) {
        var labels = LabelPositionHolder.from(disk);
        // remove labels not defined in code
        labels.removeIf(label -> !program.referencedLabels().contains(label));

        // remove labels not connected via cables
        CableNetworkManager
                .getOrRegisterNetworkFromManagerPosition(manager)
                .ifPresent(network -> labels.removeIf((label, pos) -> !network.isAdjacentToCable(pos)));

        // remove labels with no viable capability provider
        var level = manager.getLevel();
        if (level != null) {
            labels.removeIf((label, pos) -> !SFMBlockCapabilities.hasAnyCapabilityAnyDirection(level, pos));
        }
        // save new labels
        labels.save(disk);

        // update warnings
        DiskItem.setWarnings(disk, gatherWarnings(program, labels, manager));
    }

    private static void fixWarningsByModifyingMekanismAccess(
            IOStatement statement,
            LabelPositionHolder labelPositionHolder,
            Level level
    ) {
        if (!SFMModCompat.isMekanismLoaded()) return;
        DirectionQualifier directions = statement.labelAccess().directions();
        Stream<Pair<Label, BlockPos>> mekanismBlocks = statement
                .labelAccess()
                .getLabelledPositions(labelPositionHolder)
                .stream()
                .filter(pair -> level.isLoaded(pair.getSecond()))
                .filter(pair -> SFMModCompat.isMekanismBlock(level, pair.getSecond()));

        // add warning if interacting with mekanism but the mekanism side config is not ALLOW
        EnumSet<TransmissionType> referencedTransmissionTypes = SFMMekanismCompat
                .getReferencedTransmissionTypes(statement);
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
            if (level.getBlockEntity(blockPos) instanceof ISideConfiguration mekBlockEntity) {
                TileComponentConfig mekBlockEntityConfig = mekBlockEntity.getConfig();
                for (TransmissionType transmissionType : referencedTransmissionTypes) {
                    boolean anySuccess = false;
                    ConfigInfo transmissionConfig = mekBlockEntityConfig.getConfig(transmissionType);
                    if (transmissionConfig != null) {
                        Set<Direction> activeSides = getSideConfig(
                                transmissionConfig,
                                dataTypePredicate,
                                mekBlockEntity.getDirection()
                        );
                        for (Direction direction : directions) {
                            if (activeSides.contains(direction)) {
                                anySuccess = true;
                                break;
                            }
                        }
                        if (!anySuccess) {
                            // we want to enable the side for the transmission type
                            // pick the first direction in the statement
                            Direction statementSide = directions.iterator().next();
                            if (statementSide != null) {
                                RelativeSide relativeSide = RelativeSide.fromDirections(
                                        mekBlockEntity.getDirection(),
                                        statementSide
                                );
                                transmissionConfig.setDataType(
                                        fixed,
                                        relativeSide
                                );
                                mekBlockEntityConfig.sideChanged(transmissionType, relativeSide);
                            }
                        }
                    }
                }
            }
        });
    }

    private static void addWarningsForSmellyMekanismAccess(
            IOStatement ioStatement,
            LabelPositionHolder labelPositionHolder,
            IOStatement statement,
            Level level,
            ArrayList<TranslatableContents> warnings
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
            // add warning if interacting with mekanism without specifying a side
            // are any of the blocks mekanism?
            mekanismBlocks
                    .forEach(pair -> warnings.add(PROGRAM_WARNING_MEKANISM_USED_WITHOUT_DIRECTION.get(
                            pair.getFirst(),
                            statement.toStringPretty()
                    )));
        } else {
            // add warning if interacting with mekanism but the mekanism side config is not ALLOW
            EnumSet<TransmissionType> referencedTransmissionTypes = SFMMekanismCompat
                    .getReferencedTransmissionTypes(statement);
            Predicate<DataType> dataTypePredicate;
            if (ioStatement instanceof InputStatement) {
                dataTypePredicate = DataType::canOutput;
            } else if (ioStatement instanceof OutputStatement) {
                dataTypePredicate = dataType -> dataType == DataType.INPUT
                                                || dataType == DataType.INPUT_OUTPUT
                                                || dataType == DataType.INPUT_1
                                                || dataType == DataType.INPUT_2;
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
                            Set<Direction> activeSides = getSideConfig(
                                    transmissionConfig,
                                    dataTypePredicate,
                                    mekBlockEntity.getDirection()
                            );
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

    @MCVersionDependentBehaviour
    private static Set<Direction> getSideConfig(
            ConfigInfo transmissionConfig,
            Predicate<DataType> dataTypePredicate,
            Direction blockFacing
    ) {
        Set<Map.Entry<RelativeSide, DataType>> sideConfig = transmissionConfig.getSideConfig();
        Set<RelativeSide> found = new HashSet<>();
        for (var x : sideConfig) {
            if (dataTypePredicate.test(x.getValue())) {
                found.add(x.getKey());
            }
        }
        return found.stream().map(x -> x.getDirection(blockFacing)).collect(Collectors.toSet());
    }

    private static void addWarningsForUsingIOWithoutCorrespondingOppositeIO(
            Program program,
            LabelPositionHolder labelPositionHolder,
            ArrayList<TranslatableContents> warnings
    ) {
        program.tick(ProgramContext.createSimulationContext(
                program,
                labelPositionHolder,
                0,
                new GatherWarningsProgramBehaviour(warnings::addAll)
        ));
    }


    private static void addWarningsForUsingEachWithoutAPattern(
            ArrayList<TranslatableContents> warnings,
            IOStatement statement
    ) {
        boolean smells = statement
                .resourceLimits()
                .resourceLimitList()
                .stream()
                .anyMatch(rl -> rl.limit().quantity().idExpansionBehaviour()
                                == ResourceQuantity.IdExpansionBehaviour.EXPAND && !rl
                        .resourceIds()
                        .couldMatchMoreThanOne());
        if (smells) {
            warnings.add(PROGRAM_WARNING_RESOURCE_EACH_WITHOUT_PATTERN.get(statement.toStringPretty()));
        }
    }

    private static void addWarningsForSmellyRoundRobinUsage(
            ArrayList<TranslatableContents> warnings,
            IOStatement statement
    ) {
        RoundRobin roundRobin = statement.labelAccess().roundRobin();
        if (roundRobin.getBehaviour() == BY_BLOCK && statement.each()) {
            warnings.add(PROGRAM_WARNING_ROUND_ROBIN_SMELLY_EACH.get(statement.toStringPretty()));
        } else if (roundRobin.getBehaviour() == BY_LABEL
                   && statement.labelAccess().labels().size() == 1) {
            warnings.add(PROGRAM_WARNING_ROUND_ROBIN_SMELLY_COUNT.get(statement.toStringPretty()));
        }
    }

    private static void addWarningsForResourcesReferencedButNotFoundInRegistry(
            Program program,
            ArrayList<TranslatableContents> warnings
    ) {
        for (var resource : program.referencedResources()) {
            // skip regex resources
            Optional<ResourceLocation> loc = resource.getLocation();
            if (loc.isEmpty()) continue;

            // make sure resource type is registered
            var type = resource.getResourceType();
            if (type == null) {
                SFM.LOGGER.error(
                        "Resource type not found for resource: {}, should have been validated at program compile",
                        resource
                );
                continue;
            }

            // make sure resource exists in the registry
            if (!type.registryKeyExists(loc.get())) {
                warnings.add(PROGRAM_WARNING_UNKNOWN_RESOURCE_ID.get(resource));
            }
        }
    }

    private static void addWarningsForLabelsUsedInWorldButNotConnectedByCables(
            @NotNull ManagerBlockEntity manager,
            LabelPositionHolder labels,
            ArrayList<TranslatableContents> warnings,
            Level level
    ) {
        CableNetworkManager
                .getOrRegisterNetworkFromManagerPosition(manager)
                .ifPresent(network -> labels.forEach((label, pos) -> {
                    var adjacent = network.isAdjacentToCable(pos);
                    if (!adjacent) {
                        warnings.add(PROGRAM_WARNING_DISCONNECTED_LABEL.get(
                                label,
                                String.format(
                                        "[%d,%d,%d]",
                                        pos.getX(),
                                        pos.getY(),
                                        pos.getZ()
                                )
                        ));
                    }
                    var viable = SFMBlockCapabilities.hasAnyCapabilityAnyDirection(level, pos);
                    if (!viable && adjacent) {
                        warnings.add(PROGRAM_WARNING_CONNECTED_BUT_NOT_VIABLE_LABEL.get(
                                label,
                                String.format(
                                        "[%d,%d,%d]",
                                        pos.getX(),
                                        pos.getY(),
                                        pos.getZ()
                                )
                        ));
                    }
                }));
    }

    private static void addWarningsForLabelsInHolderButNotInProgram(
            Program program,
            LabelPositionHolder labels,
            ArrayList<TranslatableContents> warnings
    ) {
        labels.labels()
                .keySet()
                .stream()
                .filter(x -> !program.referencedLabels().contains(x))
                .forEach(label -> warnings.add(PROGRAM_WARNING_UNDEFINED_LABEL.get(label)));
    }

    private static void addWarningsForLabelsInProgramButNotInHolder(
            Program program,
            LabelPositionHolder labels,
            ArrayList<TranslatableContents> warnings
    ) {
        for (String label : program.referencedLabels()) {
            var isUsed = !labels.getPositions(label).isEmpty();
            if (!isUsed) {
                warnings.add(PROGRAM_WARNING_UNUSED_LABEL.get(label));
            }
        }
    }
}
