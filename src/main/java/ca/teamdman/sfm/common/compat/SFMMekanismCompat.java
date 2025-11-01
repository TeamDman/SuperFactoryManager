package ca.teamdman.sfm.common.compat;

import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.program.linting.IProgramLinter;
import ca.teamdman.sfm.common.program.linting.MekanismSideConfigProgramLinter;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.*;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import ca.teamdman.sfml.ast.DirectionQualifier;
import ca.teamdman.sfml.ast.IOStatement;
import ca.teamdman.sfml.ast.ResourceIdentifier;
import mekanism.api.RelativeSide;
import mekanism.api.math.FloatingLong;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.interfaces.ISideConfiguration;
import mekanism.common.util.UnitDisplayUtils;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class SFMMekanismCompat {
    public static @Nullable ResourceType<?, ?, ?> getResourceType(TransmissionType trans) {
        return switch (trans) {
            case ITEM -> SFMResourceTypes.ITEM.get();
            case FLUID -> SFMResourceTypes.FLUID.get();
            case GAS -> {
                ResourceLocation id = SFMResourceLocation.fromSFMPath("gas");
                yield SFMResourceTypes.registry().get(id);
            }
            case INFUSION -> {
                ResourceLocation id = SFMResourceLocation.fromSFMPath("infusion");
                yield SFMResourceTypes.registry().get(id);
            }
            case PIGMENT -> {
                ResourceLocation id = SFMResourceLocation.fromSFMPath("pigment");
                yield SFMResourceTypes.registry().get(id);
            }
            case SLURRY -> {
                ResourceLocation id = SFMResourceLocation.fromSFMPath("slurry");
                yield SFMResourceTypes.registry().get(id);
            }
            default -> null;
        };
    }

    public static EnumSet<TransmissionType> getReferencedTransmissionTypes(IOStatement statement) {
        EnumSet<TransmissionType> transmissionTypes = EnumSet.noneOf(TransmissionType.class);
        Set<? extends ResourceType<?, ?, ?>> referencedResourceTypes = statement
                .getReferencedIOResourceIds()
                .map(ResourceIdentifier::getResourceType)
                .collect(Collectors.toSet());
        for (TransmissionType transmissionType : TransmissionType.values()) {
            if (referencedResourceTypes.contains(SFMMekanismCompat.getResourceType(transmissionType))) {
                transmissionTypes.add(transmissionType);
            }
        }
        return transmissionTypes;
    }

    public static FloatingLong createForgeEnergy(long amount) {
        return UnitDisplayUtils.EnergyUnit.FORGE_ENERGY.convertInPlaceFrom(FloatingLong.create(amount));
    }

    public static String gatherInspectionResults(BlockEntity blockEntity) {
        if (!(blockEntity instanceof ISideConfiguration sideConfiguration)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("-- Mekanism stuff\n");
        TileComponentConfig config = sideConfiguration.getConfig();
        for (TransmissionType type : TransmissionType.values()) {
            var resourceType = getResourceType(type);
            if (resourceType == null) {
                continue;
            }

            var maybeResourceTypeKe = SFMResourceTypes.registry().getKey(resourceType);
            if (maybeResourceTypeKe.isEmpty()) {
                continue;
            }
            var resourceTypeKey = maybeResourceTypeKe.get();

            ConfigInfo info = config.getConfig(type);
            if (info == null) {
                continue;
            }

            Set<Direction> outputSides = info.getSides(DataType::canOutput);
            if (!outputSides.isEmpty()) {
                sb
                        .append("-- ")
                        .append(LocalizationKeys.CONTAINER_INSPECTOR_MEKANISM_MACHINE_OUTPUTS.getStub())
                        .append("\n");
                sb.append("INPUT ").append(resourceTypeKey.location()).append(":: FROM target ");
                sb.append(outputSides
                                  .stream()
                                  .map(DirectionQualifier::directionToString)
                                  .collect(Collectors.joining(", ")));
                sb.append(" SIDE\n");
            }

            Set<Direction> inputSides = new HashSet<>();
            for (RelativeSide side : RelativeSide.values()) {
                DataType dataType = info.getDataType(side);
                if (dataType == DataType.INPUT
                    || dataType == DataType.INPUT_1
                    || dataType == DataType.INPUT_2
                    || dataType == DataType.INPUT_OUTPUT) {
                    inputSides.add(side.getDirection(sideConfiguration.getDirection()));
                }
            }
            if (!inputSides.isEmpty()) {
                sb
                        .append("-- ")
                        .append(LocalizationKeys.CONTAINER_INSPECTOR_MEKANISM_MACHINE_INPUTS.getStub())
                        .append("\n");
                sb.append("OUTPUT ").append(resourceTypeKey.location()).append(":: TO target ");
                sb.append(inputSides
                                  .stream()
                                  .map(DirectionQualifier::directionToString)
                                  .collect(Collectors.joining(", ")));
                sb.append(" SIDE\n");
            }
        }
        return sb.toString();
    }

    public static void registerResourceTypes(SFMDeferredRegister<ResourceType<?, ?, ?>> types) {
        types.register(
                "gas",
                GasResourceType::new
        );
        types.register(
                "infusion",
                InfuseResourceType::new
        );

        types.register(
                "pigment",
                PigmentResourceType::new
        );
        types.register(
                "slurry",
                SlurryResourceType::new
        );
        types.register(
                "mekanism_energy",
                MekanismEnergyResourceType::new
        );
    }

    public static void registerProgramLinters(SFMDeferredRegister<IProgramLinter> types) {
        types.register(
                "mekanism",
                MekanismSideConfigProgramLinter::new
        );
    }

    public static void configureExclusiveIO(
            ISideConfiguration mekanismBlockEntity,
            TransmissionType transmissionType,
            RelativeSide relativeSide,
            DataType dataType
    ) {
        TileComponentConfig config = mekanismBlockEntity.getConfig();
        for (TransmissionType value : TransmissionType.values()) {
            ConfigInfo info = config.getConfig(value);
            if (info == null) continue;
            for (RelativeSide side : RelativeSide.values()) {
                info.setDataType(
                        value == transmissionType && side == relativeSide ? dataType : DataType.NONE,
                        side
                );
            }
            config.sideChanged(value, relativeSide);
        }
    }
}
