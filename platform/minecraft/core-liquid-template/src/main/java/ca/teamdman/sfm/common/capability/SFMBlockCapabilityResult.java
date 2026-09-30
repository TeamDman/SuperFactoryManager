package ca.teamdman.sfm.common.capability;

import ca.teamdman.sfm.common.registry.registration.SFMGlobalBlockCapabilityProviders;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullConsumer;
{% when '1.20.2' %}
import net.neoforged.neoforge.common.util.LazyOptional;
import net.neoforged.neoforge.common.util.NonNullConsumer;
import org.jetbrains.annotations.NotNull;
{% when '1.20.3', '1.20.4' %}
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.CapabilityListenerHolder;
import net.neoforged.neoforge.capabilities.ICapabilityInvalidationListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.CapabilityListenerHolder;
import net.neoforged.neoforge.capabilities.ICapabilityInvalidationListener;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
{% endcase %}

/// In Minecraft before 1.20.3, NeoForge uses {@code LazyOptional<T>} for the type of retrieved Capabilities.
/// In Minecraft 1.20.3 and later, {@code @Nullable T} is used instead.
/// Between Minecraft 1.20 and Minecraft 1.20.1, SFM switches from using Forge to NeoForge.
/// The package path for many classes changes in this transition.
/// To minimize entropy in the SFM codebase, we wrap the different optional types in {@link SFMBlockCapabilityResult}
/// Capabilities are retrieved by querying {@link SFMGlobalBlockCapabilityProviders} with a {@link SFMBlockCapabilityKind}
///
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
/// Note that we MUST hold a STRONG reference to the {@link ICapabilityInvalidationListener}
/// so that {@link CapabilityListenerHolder} doesn't drop our listener without it being called.
///
{% endcase %}
/// This class helps keep {@link MCVersionDependentBehaviour} out of other classes.
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
@SuppressWarnings("UnstableApiUsage") // javadoc lol
{% endcase %}
@MCVersionDependentBehaviour
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
public record SFMBlockCapabilityResult<CAP>(LazyOptional<CAP> inner) {
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
public record SFMBlockCapabilityResult<CAP>(
        /// The inner mod platform capability object
        @Nullable CAP inner,
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
    public static <CAP> SFMBlockCapabilityResult<CAP> of(LazyOptional<CAP> capability) {
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
        /// The holder of references to invalidation listeners that must be kept alive to avoid garbage collection
        Set<ICapabilityInvalidationListener> listeners
) {
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
        return new SFMBlockCapabilityResult<>(capability);
    }
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
    public static <CAP> SFMBlockCapabilityResult<CAP> of(@Nullable CAP capability) {
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
    public static <CAP> SFMBlockCapabilityResult<CAP> of(CAP capability) {

        return new SFMBlockCapabilityResult<>(LazyOptional.of(() -> capability));
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
        return new SFMBlockCapabilityResult<>(capability, new HashSet<>(1));
{% endcase %}
    }

    public static <CAP> SFMBlockCapabilityResult<CAP> empty() {

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
        return SFMBlockCapabilityResult.of(LazyOptional.empty());
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
        return SFMBlockCapabilityResult.of(null);
{% endcase %}
    }

    public CAP unwrap() {

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
        return inner.orElseThrow(IllegalStateException::new);
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
        return Objects.requireNonNull(inner);
{% endcase %}
    }

    public boolean isPresent() {

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
        return inner.isPresent();
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
        return inner != null;
{% endcase %}
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
    /// If this capability is not present, the listener is called immediately.
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
    @MCVersionDependentBehaviour
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
    public void addInvalidationListener(NonNullConsumer<SFMBlockCapabilityResult<CAP>> listener) {
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
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
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
        inner.addListener(inner -> listener.accept(SFMBlockCapabilityResult.this));
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
    }

}
