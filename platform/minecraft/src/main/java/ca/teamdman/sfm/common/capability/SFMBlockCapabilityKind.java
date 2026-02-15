package ca.teamdman.sfm.common.capability;

import java.util.function.Supplier;

import net.minecraftforge.common.capabilities.Capability;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.github.bsideup.jabel.Desugar;

import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer;
import ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer.ResourceType;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;

/// In NeoForge for Minecraft 1.20.3, the {@code Capability<CAP>} type is replaced with {@code BlockCapability<CAP,
/// CONTEXT>}.
/// We use {@link SFMBlockCapabilityKind} to wrap the capability kind.
/// We use {@link SFMBlockCapabilityResult} to wrap the results of capability queries.
/// This wrapper minimizes entropy in the codebase by isolating the differences in the capability kind.
///
/// This class helps keep {@link MCVersionDependentBehaviour} out of other classes.
@Desugar
@MCVersionDependentBehaviour
public record SFMBlockCapabilityKind<CAP>(
        Supplier<Capability<CAP>> capabilityKind
) {
    public String getName() {
        return capabilityKind.get().getName();
    }

    @Override
    public @NotNull String toString() {
        return "SFMBlockCapabilityKind[" + getName() + "]";
    }

    @SuppressWarnings("unchecked")
    public <STACK, ITEM> @Nullable ResourceType<STACK, ITEM, CAP> getResourceType() {
        return (ResourceType<STACK, ITEM, CAP>) SFMResourceTypes
                .registry()
                .stream()
                .map(ResourceTypeContainer::get)
                .filter(resourceType -> resourceType.CAPABILITY_KIND.equals(this))
                .findFirst()
                .orElse(null);
    }
}
