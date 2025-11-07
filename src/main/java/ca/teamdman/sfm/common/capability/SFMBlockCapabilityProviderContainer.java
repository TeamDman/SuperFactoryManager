package ca.teamdman.sfm.common.capability;

import net.minecraftforge.registries.IForgeRegistryEntry;

abstract public class SFMBlockCapabilityProviderContainer extends IForgeRegistryEntry.Impl<SFMBlockCapabilityProviderContainer> {
    abstract public SFMBlockCapabilityProvider<?> get();
}
