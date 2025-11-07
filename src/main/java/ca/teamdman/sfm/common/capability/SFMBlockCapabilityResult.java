package ca.teamdman.sfm.common.capability;

import ca.teamdman.sfm.common.registry.SFMGlobalBlockCapabilityProviders;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/// In Minecraft before 1.20.3, NeoForge uses {@code Optional<T>} for the type of retrieved Capabilities.
/// In Minecraft 1.20.3 and later, {@code @Nullable T} is used instead.
/// Between Minecraft 1.20 and Minecraft 1.20.1, SFM switches from using Forge to NeoForge.
/// The package path for many classes changes in this transition.
/// To minimize entropy in the SFM codebase, we wrap the different optional types in {@link SFMBlockCapabilityResult}
/// Capabilities are retrieved by querying {@link SFMGlobalBlockCapabilityProviders} with a {@link SFMBlockCapabilityKind}
///
/// This class helps keep {@link MCVersionDependentBehaviour} out of other classes.
@MCVersionDependentBehaviour
public record SFMBlockCapabilityResult<CAP>(Optional<CAP> capability) {

    public static <CAP> SFMBlockCapabilityResult<CAP> of(Optional<CAP> capability) {
        return new SFMBlockCapabilityResult<>(capability);
    }

    public static <CAP> SFMBlockCapabilityResult<CAP> of(CAP capability) {
        return new SFMBlockCapabilityResult<>(Optional.of(() -> capability));
    }

    public static <CAP> SFMBlockCapabilityResult<CAP> empty() {
        return SFMBlockCapabilityResult.of(Optional.empty());
    }

    public @NotNull CAP unwrap() {
        return capability.orElseThrow(IllegalStateException::new);
    }

    public boolean isPresent() {
        return capability.isPresent();
    }

    public void addListener(NonNullConsumer<Optional<CAP>> listener) {
        capability.addListener(listener);
    }
}
