package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class TestBarrelBlockEntity extends BarrelBlockEntity {
    public TestBarrelBlockEntity(
            BlockPos pPos,
            BlockState pBlockState
    ) {
        super(pPos, pBlockState);
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21' %}
    //    @Override
    @SuppressWarnings("unused") // 1.21.1 only
{% when '1.21.1', '26.1.2' %}
    @Override
{% endcase %}
    public boolean isValidBlockState(BlockState blockState) {
        return SFMBlockEntities.TEST_BARREL.get().isValid(blockState);
    }
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
//        super.preRemoveSideEffects(pos, state);
    }
{% endcase %}
}
