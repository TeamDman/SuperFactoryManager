package ca.teamdman.sfm.common.capability;

import ca.teamdman.sfm.common.registry.SFMGlobalBlockCapabilityProviders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.CauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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
public interface SFMBlockCapabilityProvider<CAP> {
    ///  Used to determine which providers to ask when we are looking for a specific capability kind
    boolean matchesCapabilityKind(SFMBlockCapabilityKind<?> capabilityKind);

    ///  Returns a capability for the given block at the given position in the given level if it has one.
    SFMBlockCapabilityResult<CAP> getCapability(
            SFMBlockCapabilityKind<CAP> capabilityKind,
            LevelAccessor level,
            BlockPos pos,
            BlockState state,
            @Nullable
            BlockEntity blockEntity,
            @Nullable Direction direction
    );

    ///  Higher priority providers are checked first. The Default priority is 0.
    default int priority() {
        return 0;
    }
}
