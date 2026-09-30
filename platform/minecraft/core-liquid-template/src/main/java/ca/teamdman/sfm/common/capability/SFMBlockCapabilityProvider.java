package ca.teamdman.sfm.common.capability;

import ca.teamdman.sfm.common.registry.registration.SFMGlobalBlockCapabilityProviders;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% endcase %}
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.CauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import net.neoforged.neoforge.capabilities.IBlockCapabilityProvider;
{% endcase %}
import org.jetbrains.annotations.Nullable;

/// In NeoForge for Minecraft before 1.20.3, capabilities are queried from {@link BlockEntity}.
/// We wrap retrieved capabilities in {@link SFMBlockCapabilityResult}.
/// Capabilities are queried using a {@link SFMBlockCapabilityKind} like {@link SFMWellKnownCapabilities#ITEM_HANDLER}.
///
/// In Minecraft 1.20.3 and later, capabilities are instead queried from {@link Level}.
/// Additionally, mods can participate in the registration of capabilities to blocks in that version.
///
/// Prior to this version, for SFM to get a {@link SFMWellKnownCapabilities#FLUID_HANDLER} for {@link CauldronBlock},
/// it has its own mechanism for indirection via {@link SFMGlobalBlockCapabilityProviders} and {@link SFMBlockCapabilityProviderDiscovery}
///
/// For per-mod compat, like to fix <a href="https://github.com/TeamDman/SuperFactoryManager/issues/322">#322</a>,
/// SFM keeps this indirection mechanism for 1.20.3 and later as well.
///
/// TODO: Fix <a href="https://github.com/TeamDman/SuperFactoryManager/issues/352">#352</a> using this.
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
///
/// This class is used for both global registration of capabilities and local registration.
/// For example, SFM adds {@link CauldronBlock} support for {@link net.neoforged.neoforge.fluids.capability.IFluidHandler}
/// in a global way, which lets other mods use cauldrons as fluid containers without needing to add support themselves.
///
/// However, this class is also used for local registrations, which only SFM sees from the way SFM discovers capabilities.
{% endcase %}
public interface SFMBlockCapabilityProvider<CAP> {
    ///  Used to determine which providers to ask when we are looking for a specific capability kind
    boolean matchesCapabilityKind(SFMBlockCapabilityKind<?> capabilityKind);

    ///  Returns a capability for the given block at the given position in the given level if it has one.
    SFMBlockCapabilityResult<CAP> getCapability(
            SFMBlockCapabilityKind<CAP> capabilityKind,
            LevelAccessor level,
            BlockPos pos,
            BlockState state,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
            @Nullable
            BlockEntity blockEntity,
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
            @Nullable BlockEntity blockEntity,
{% endcase %}
            @Nullable Direction direction
    );

    ///  Higher priority providers are checked first. The Default priority is 0.
    default int priority() {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}

{% endcase %}
        return 0;
    }
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}

    @MCVersionDependentBehaviour
    @SuppressWarnings("Convert2Lambda")
    default IBlockCapabilityProvider<CAP, @Nullable Direction> specialize(SFMBlockCapabilityKind<CAP> capabilityKind) {

        return new IBlockCapabilityProvider<>() {
            @Override
            public @Nullable CAP getCapability(
                    Level level,
                    BlockPos pos,
                    BlockState state,
                    @Nullable BlockEntity blockEntity,
                    Direction context
            ) {

                return SFMBlockCapabilityProvider.this.getCapability(
                        capabilityKind,
                        level,
                        pos,
                        state,
                        blockEntity,
                        context
                ).inner();
            }
        };
    }

{% endcase %}
}
