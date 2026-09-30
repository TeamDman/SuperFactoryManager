package ca.teamdman.sfm.common.capability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import net.neoforged.neoforge.common.extensions.ILevelExtension;
{% endcase %}
import org.jetbrains.annotations.Nullable;

/// In NeoForge for Minecraft 1.20.3, the way capabilities are discovered changed.
/// See {@link SFMBlockCapabilityProvider} for more information.
/// This is the fallback provider for the "built-in" behaviour provided by the modding framework.
public class BlockEntityCapabilityProvider implements SFMBlockCapabilityProvider<Object> {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}

{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
        if (blockEntity != null) {;
            var result = blockEntity.getCapability(capabilityKind.capabilityKind(), direction);
            if (result.isPresent()) {
                return SFMBlockCapabilityResult.of(result);
            }
        }
        return SFMBlockCapabilityResult.empty();
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
        if (!(level instanceof ILevelExtension capLevel)) return SFMBlockCapabilityResult.empty();
        return SFMBlockCapabilityResult.of(capLevel.getCapability(
                capabilityKind.capabilityKind(),
                pos,
                state,
                blockEntity,
                direction
        ));
{% endcase %}
    }

    @Override
    public int priority() {
        return -100; // check this one last
    }
}
