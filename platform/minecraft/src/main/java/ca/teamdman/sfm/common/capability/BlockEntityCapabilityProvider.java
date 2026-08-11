package ca.teamdman.sfm.common.capability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.extensions.ILevelExtension;
import org.jetbrains.annotations.Nullable;

/// In NeoForge for Minecraft 1.20.3, the way capabilities are discovered changed.
/// See {@link SFMBlockCapabilityProvider} for more information.
/// This is the fallback provider for the "built-in" behaviour provided by the modding framework.
public class BlockEntityCapabilityProvider implements SFMBlockCapabilityProvider<Object> {

    @Override
    public boolean matchesCapabilityKind(SFMBlockCapabilityKind<?> capabilityKind) {
        return true;
    }

    @Override
    public SFMBlockCapabilityResult<Object> getCapability(
            SFMBlockCapabilityKind<Object> capabilityKind,
            LevelAccessor level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity blockEntity,
            @Nullable Direction direction
    ) {
        if (!(level instanceof ILevelExtension capLevel)) return SFMBlockCapabilityResult.empty();
        return SFMBlockCapabilityResult.of(capLevel.getCapability(
                capabilityKind.capabilityKind(),
                pos,
                state,
                blockEntity,
                direction
        ));
    }

    @Override
    public int priority() {
        return -100; // check this one last
    }
}
