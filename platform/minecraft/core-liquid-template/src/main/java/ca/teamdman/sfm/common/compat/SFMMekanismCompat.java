package ca.teamdman.sfm.common.compat;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.program.linting.IProgramLinter;
import ca.teamdman.sfm.common.program.linting.compat.mekanism.MekanismSidednessProgramLinter;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.4', '1.21' %}
import ca.teamdman.sfm.common.resourcetype.*;
{% when '1.20.2', '1.20.3' %}
import ca.teamdman.sfm.common.resourcetype.ResourceType;
{% when '1.21.1', '26.1.2' %}
import ca.teamdman.sfm.common.resourcetype.ChemicalResourceType;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
{% endcase %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import ca.teamdman.sfml.ast.IOStatement;
import ca.teamdman.sfml.ast.ResourceIdentifier;
import ca.teamdman.sfml.ast.Side;
import mekanism.api.RelativeSide;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import mekanism.api.math.FloatingLong;
{% when '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.interfaces.ISideConfiguration;
import mekanism.common.util.UnitDisplayUtils;
import net.minecraft.core.Direction;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21' %}
import net.minecraft.resources.ResourceLocation;
{% when '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.core.Holder;
{% endcase %}
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashSet;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3' %}
{% when '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import java.util.Map;
{% endcase %}
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SFMMekanismCompat {
    @SFMLocalizationDatagen
    public static final LocalizationEntry CONTAINER_INSPECTOR_MEKANISM_MACHINE_INPUTS = new LocalizationEntry(
            "gui.sfm.container_inspector.mekanism_machine_inputs",
            "The following are based on the MACHINE'S input config"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry CONTAINER_INSPECTOR_MEKANISM_MACHINE_OUTPUTS = new LocalizationEntry(
            "gui.sfm.container_inspector.mekanism_machine_outputs",
            "The following are based on the MACHINE'S output config"
    );

    public static @Nullable ResourceType<?, ?, ?> getResourceType(TransmissionType trans) {

        return switch (trans) {
            case ITEM -> SFMResourceTypes.ITEM.get();
            case FLUID -> SFMResourceTypes.FLUID.get();
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21' %}
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
{% when '1.21.1' %}
//            case GAS -> {
//                ResourceLocation id = SFMResourceLocation.fromSFMPath("gas");
//                yield SFMResourceTypes.registry().get(id);
//            }
//            case INFUSION -> {
//                ResourceLocation id = SFMResourceLocation.fromSFMPath("infusion");
//                yield SFMResourceTypes.registry().get(id);
//            }
//            case PIGMENT -> {
//                ResourceLocation id = SFMResourceLocation.fromSFMPath("pigment");
//                yield SFMResourceTypes.registry().get(id);
//            }
//            case SLURRY -> {
//                ResourceLocation id = SFMResourceLocation.fromSFMPath("slurry");
//                yield SFMResourceTypes.registry().get(id);
//            }
            case ENERGY -> SFMResourceTypes.FORGE_ENERGY.get();
            case CHEMICAL -> SFMResourceTypes.registry()
                    .get(SFMResourceLocation.fromSFMPath("chemical"));
{% when '26.1.2' %}
//            case GAS -> {
//                Identifier id = SFMResourceLocation.fromSFMPath("gas");
//                yield SFMResourceTypes.registry().get(id);
//            }
//            case INFUSION -> {
//                Identifier id = SFMResourceLocation.fromSFMPath("infusion");
//                yield SFMResourceTypes.registry().get(id);
//            }
//            case PIGMENT -> {
//                Identifier id = SFMResourceLocation.fromSFMPath("pigment");
//                yield SFMResourceTypes.registry().get(id);
//            }
//            case SLURRY -> {
//                Identifier id = SFMResourceLocation.fromSFMPath("slurry");
//                yield SFMResourceTypes.registry().get(id);
//            }
            case ENERGY -> SFMResourceTypes.FORGE_ENERGY.get();
            case CHEMICAL -> SFMResourceTypes.registry()
                    .get(SFMResourceLocation.fromSFMPath("chemical"))
                    .map(Holder.Reference::value)
                    .orElse(null);
{% endcase %}
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

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
    public static FloatingLong createForgeEnergy(long amount) {
{% when '1.21', '1.21.1', '26.1.2' %}
    public static long createForgeEnergy(long amount) {
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        return UnitDisplayUtils.EnergyUnit.FORGE_ENERGY.convertInPlaceFrom(FloatingLong.create(amount));
{% when '1.21', '1.21.1', '26.1.2' %}
        return UnitDisplayUtils.EnergyUnit.FORGE_ENERGY.convertFrom(amount);
{% endcase %}
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3' %}
    @SuppressWarnings("unused")
{% when '1.20.4', '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
    @MCVersionDependentBehaviour
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.21.1', '26.1.2' %}
    public static Set<Direction> getSides(
            ConfigInfo config,
            ISideConfiguration facing,
            Predicate<DataType> condition
    ) {
{% when '1.20.4', '1.21' %}
    public static Set<Direction> getSides(ConfigInfo config, ISideConfiguration facing, Predicate<DataType> condition) {
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3' %}
        return config.getSides(condition);
{% when '1.20.4', '1.21', '1.21.1', '26.1.2' %}

        Set<Direction> rtn = EnumSet.noneOf(Direction.class);
        for (Map.Entry<RelativeSide, DataType> entry : config.getSideConfig()) {
            if (condition.test(entry.getValue())) {
                rtn.add(entry.getKey().getDirection(facing.getDirection()));
            }
        }
        return rtn;
{% endcase %}
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

            Set<Direction> outputSides = getSides(info, sideConfiguration, DataType::canOutput);
            if (!outputSides.isEmpty()) {
                sb
                        .append("-- ")
                        .append(CONTAINER_INSPECTOR_MEKANISM_MACHINE_OUTPUTS.getStub())
                        .append("\n");
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                sb.append("INPUT ").append(resourceTypeKey.location()).append(":: FROM target ");
{% when '26.1.2' %}
                sb.append("INPUT ").append(resourceTypeKey.identifier()).append(":: FROM target ");
{% endcase %}
                sb.append(outputSides
                                  .stream()
                                  .map(Side::fromDirection)
                                  .map(Side::toString)
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
                        .append(CONTAINER_INSPECTOR_MEKANISM_MACHINE_INPUTS.getStub())
                        .append("\n");
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                sb.append("OUTPUT ").append(resourceTypeKey.location()).append(":: TO target ");
{% when '26.1.2' %}
                sb.append("OUTPUT ").append(resourceTypeKey.identifier()).append(":: TO target ");
{% endcase %}
                sb.append(inputSides
                                  .stream()
                                  .map(Side::fromDirection)
                                  .map(Side::toString)
                                  .collect(Collectors.joining(", ")));
                sb.append(" SIDE\n");
            }
        }
        return sb.toString();
    }

    public static void registerResourceTypes(SFMDeferredRegister<ResourceType<?, ?, ?>> types) {

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21' %}
{% when '1.21.1', '26.1.2' %}
        types.register("chemical", ChemicalResourceType::new);
{% endcase %}
        types.register(
                "gas",
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21' %}
                GasResourceType::new
{% when '1.21.1', '26.1.2' %}
                ChemicalResourceType::new
{% endcase %}
        );
        types.register(
                "infusion",
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21' %}
                InfuseResourceType::new
{% when '1.21.1', '26.1.2' %}
                ChemicalResourceType::new
{% endcase %}
        );

        types.register(
                "pigment",
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21' %}
                PigmentResourceType::new
{% when '1.21.1', '26.1.2' %}
                ChemicalResourceType::new
{% endcase %}
        );
        types.register(
                "slurry",
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3' %}
                SlurryResourceType::new
        );
        types.register(
                "mekanism_energy",
                MekanismEnergyResourceType::new
        );
{% when '1.20.4', '1.21' %}
                SlurryResourceType::new
        );
//        types.register(
//                "mekanism_energy",
//                MekanismEnergyResourceType::new
//        );
{% when '1.21.1', '26.1.2' %}
                ChemicalResourceType::new
        );
//        types.register(
//                "mekanism_energy",
//                MekanismEnergyResourceType::new
//        );
{% endcase %}
    }

    public static void registerProgramLinters(SFMDeferredRegister<IProgramLinter> types) {

        types.register(
                "mekanism_sidedness",
                MekanismSidednessProgramLinter::new
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
