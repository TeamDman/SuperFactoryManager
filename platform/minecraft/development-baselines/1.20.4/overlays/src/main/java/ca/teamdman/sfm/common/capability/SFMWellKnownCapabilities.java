package ca.teamdman.sfm.common.capability;


import ca.teamdman.sfm.common.registry.registration.SFMCapabilities;
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.stream.Stream;


/// In between Forge for Minecraft 1.19.2 and NeoForge for Minecraft 1.20.3,
/// the {@code ForgeCapabilities} class is changed to {@code BuiltInCapabilities}
/// and later again to {@code Capabilities.ItemHandler.BLOCK}
@MCVersionDependentBehaviour
public class SFMWellKnownCapabilities {
    public static final SFMBlockCapabilityKind<IEnergyStorage> ENERGY
            = new SFMBlockCapabilityKind<>(Capabilities.EnergyStorage.BLOCK);
    public static final SFMBlockCapabilityKind<IFluidHandler> FLUID_HANDLER
            = new SFMBlockCapabilityKind<>(Capabilities.FluidHandler.BLOCK);
    public static final SFMBlockCapabilityKind<IItemHandler> ITEM_HANDLER
            = new SFMBlockCapabilityKind<>(Capabilities.ItemHandler.BLOCK);
    public static final SFMBlockCapabilityKind<IRedstoneSignalStorage> REDSTONE_HANDLER
            = SFMCapabilities.REDSTONE_HANDLER;

    public static Stream<SFMBlockCapabilityKind<?>> streamCapabilities() {
        return SFMResourceTypes.registry().stream().map(ResourceType::capabilityKind);
    }
}
