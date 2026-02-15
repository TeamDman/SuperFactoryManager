package ca.teamdman.sfm.common.capability;

import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer;
import ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer.ResourceType;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.stream.Collectors;
import java.util.stream.Stream;


/// In between Forge for Minecraft 1.19.2 and NeoForge for Minecraft 1.20.3,
/// the {@code ForgeCapabilities} class is changed to {@code BuiltInCapabilities}
/// and later again to {@code Capabilities.ItemHandler.BLOCK}
@MCVersionDependentBehaviour
public class SFMWellKnownCapabilities {

    @CapabilityInject(IRedstoneSignalStorage.class)
    public static Capability<IRedstoneSignalStorage> BASE_REDSTONE_HANDLER;

    public static final SFMBlockCapabilityKind<IEnergyStorage> ENERGY
            = new SFMBlockCapabilityKind<>(() -> CapabilityEnergy.ENERGY);
    public static final SFMBlockCapabilityKind<IFluidHandler> FLUID_HANDLER = new SFMBlockCapabilityKind<>(
            () -> CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY);
    public static final SFMBlockCapabilityKind<IItemHandler> ITEM_HANDLER = new SFMBlockCapabilityKind<>(
            () -> CapabilityItemHandler.ITEM_HANDLER_CAPABILITY);
    public static final SFMBlockCapabilityKind<IRedstoneSignalStorage> REDSTONE_HANDLER = new SFMBlockCapabilityKind<>(
            () -> BASE_REDSTONE_HANDLER);

    public static Stream<SFMBlockCapabilityKind<?>> streamCapabilities() {
        return SFMResourceTypes.registry().values().stream().map(ResourceTypeContainer::get)
                .map(ResourceType::capabilityKind);
    }

    public static Iterable<SFMBlockCapabilityKind<?>> getCapabilities() {
        return SFMResourceTypes.registry().values().stream().map(ResourceTypeContainer::get).map(ResourceType::capabilityKind).collect(Collectors.toSet());
    }


}
