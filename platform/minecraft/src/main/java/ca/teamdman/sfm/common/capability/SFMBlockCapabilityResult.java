package ca.teamdman.sfm.common.capability;

import ca.teamdman.sfm.common.registry.registration.SFMGlobalBlockCapabilityProviders;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.CapabilityListenerHolder;
import net.neoforged.neoforge.capabilities.ICapabilityInvalidationListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/// In Minecraft before 1.20.3, NeoForge uses {@code LazyOptional<T>} for the type of retrieved Capabilities.
/// In Minecraft 1.20.3 and later, {@code @Nullable T} is used instead.
/// Between Minecraft 1.20 and Minecraft 1.20.1, SFM switches from using Forge to NeoForge.
/// The package path for many classes changes in this transition.
/// To minimize entropy in the SFM codebase, we wrap the different optional types in {@link SFMBlockCapabilityResult}
/// Capabilities are retrieved by querying {@link SFMGlobalBlockCapabilityProviders} with a {@link SFMBlockCapabilityKind}
///
/// Note that we MUST hold a STRONG reference to the {@link ICapabilityInvalidationListener}
/// so that {@link CapabilityListenerHolder} doesn't drop our listener without it being called.
///
/// This class helps keep {@link MCVersionDependentBehaviour} out of other classes.
@SuppressWarnings("UnstableApiUsage") // javadoc lol
@MCVersionDependentBehaviour
public record SFMBlockCapabilityResult<CAP>(
        /// The inner mod platform capability object
        @Nullable CAP inner,

        /// The holder of references to invalidation listeners that must be kept alive to avoid garbage collection
        Set<ICapabilityInvalidationListener> listeners
) {

    public static <CAP> SFMBlockCapabilityResult<CAP> of(@Nullable CAP capability) {

        return new SFMBlockCapabilityResult<>(capability, new HashSet<>(1));
    }

    public static <CAP> SFMBlockCapabilityResult<CAP> empty() {

        return SFMBlockCapabilityResult.of(null);
    }

    public CAP unwrap() {

        return Objects.requireNonNull(inner);
    }

    public boolean isPresent() {

        return inner != null;
    }

    @MCVersionDependentBehaviour
    public void addInvalidationListener(
            ICapabilityInvalidationListener listener,
            ServerLevel serverLevel,
            BlockPos pos
    ) {

        // Register the listener to the level; it stores a weak reference
        serverLevel.registerCapabilityListener(pos, listener);

        // Ensure the listener object lives as long as this result object by tracking a strong reference
        // We MUST avoid it getting garbage collected by CapabilityListenerHolder
        this.listeners.add(listener);

    }

}
