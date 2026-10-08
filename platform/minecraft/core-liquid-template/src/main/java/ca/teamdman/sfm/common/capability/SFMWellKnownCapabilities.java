package ca.teamdman.sfm.common.capability;

{% case minecraft_version %}
{% when "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import ca.teamdman.sfm.common.registry.registration.SFMCapabilities;
{% endcase %}
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
{% when "1.20.2" %}
import net.neoforged.neoforge.common.capabilities.Capabilities;
import net.neoforged.neoforge.common.capabilities.CapabilityManager;
import net.neoforged.neoforge.common.capabilities.CapabilityToken;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
{% when "26.1.2" %}
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
{% else %}
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
{% endcase %}

import java.util.stream.Stream;

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import static net.minecraftforge.common.capabilities.CapabilityManager.get;
{% endcase %}

/// In between Forge for Minecraft 1.19.2 and NeoForge for Minecraft 1.20.3,
/// the {@code ForgeCapabilities} class is changed to {@code BuiltInCapabilities}
/// and later again to {@code Capabilities.ItemHandler.BLOCK}
@MCVersionDependentBehaviour
public class SFMWellKnownCapabilities {
{% case minecraft_version %}
{% when "26.1.2" %}
    public static final SFMBlockCapabilityKind<EnergyHandler> ENERGY
{% else %}
    public static final SFMBlockCapabilityKind<IEnergyStorage> ENERGY
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
            = new SFMBlockCapabilityKind<>(ForgeCapabilities.ENERGY);
{% when "1.20.2" %}
            = new SFMBlockCapabilityKind<>(Capabilities.ENERGY);
{% when "26.1.2" %}
            = new SFMBlockCapabilityKind<>(Capabilities.Energy.BLOCK);
{% else %}
            = new SFMBlockCapabilityKind<>(Capabilities.EnergyStorage.BLOCK);
{% endcase %}
{% case minecraft_version %}
{% when "26.1.2" %}
    public static final SFMBlockCapabilityKind<ResourceHandler<FluidResource>> FLUID_HANDLER
{% else %}
    public static final SFMBlockCapabilityKind<IFluidHandler> FLUID_HANDLER
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
            = new SFMBlockCapabilityKind<>(ForgeCapabilities.FLUID_HANDLER);
{% when "1.20.2" %}
            = new SFMBlockCapabilityKind<>(Capabilities.FLUID_HANDLER);
{% when "26.1.2" %}
            = new SFMBlockCapabilityKind<>(Capabilities.Fluid.BLOCK);
{% else %}
            = new SFMBlockCapabilityKind<>(Capabilities.FluidHandler.BLOCK);
{% endcase %}
{% case minecraft_version %}
{% when "26.1.2" %}
    public static final SFMBlockCapabilityKind<ResourceHandler<ItemResource>> ITEM_HANDLER
{% else %}
    public static final SFMBlockCapabilityKind<IItemHandler> ITEM_HANDLER
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
            = new SFMBlockCapabilityKind<>(ForgeCapabilities.ITEM_HANDLER);
{% when "1.20.2" %}
            = new SFMBlockCapabilityKind<>(Capabilities.ITEM_HANDLER);
{% when "26.1.2" %}
            = new SFMBlockCapabilityKind<>(Capabilities.Item.BLOCK);
{% else %}
            = new SFMBlockCapabilityKind<>(Capabilities.ItemHandler.BLOCK);
{% endcase %}
    public static final SFMBlockCapabilityKind<IRedstoneSignalStorage> REDSTONE_HANDLER
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
            = new SFMBlockCapabilityKind<>(get(new CapabilityToken<>() {
    }));
{% when "1.20.2" %}
            = new SFMBlockCapabilityKind<>(CapabilityManager.get(new CapabilityToken<>() {
    }));
{% else %}
            = SFMCapabilities.REDSTONE_HANDLER;
{% endcase %}
{% if features.image_resources %}
    public static final SFMBlockCapabilityKind<IImageHandler> IMAGE_HANDLER
            = new SFMBlockCapabilityKind<>(get(new CapabilityToken<>() {
    }));
{% endif %}

    public static Stream<SFMBlockCapabilityKind<?>> streamCapabilities() {
        return SFMResourceTypes.registry().stream().map(ResourceType::capabilityKind);
    }
}
