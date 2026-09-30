package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import net.minecraft.core.BlockPos;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
import net.minecraft.world.level.block.state.BlockState;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.Nullable;
{% when '1.20.2' %}
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.capabilities.Capability;
import net.neoforged.neoforge.common.util.LazyOptional;
import org.jetbrains.annotations.Nullable;
{% when '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.neoforged.neoforge.client.model.data.ModelData;
{% when '26.1.2' %}
import net.neoforged.neoforge.model.data.ModelData;
{% endcase %}

import static ca.teamdman.sfm.common.blockentity.FancyCableFacadeBlockEntity.FACADE_DIRECTION;

public class TunnelledFancyCableFacadeBlockEntity extends CommonFacadeBlockEntity {
    public TunnelledFancyCableFacadeBlockEntity(
            BlockPos blockPos,
            BlockState blockState
    ) {

        super(SFMBlockEntities.TUNNELLED_FANCY_CABLE_FACADE.get(), blockPos, blockState);
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
    @Override
    public <T> LazyOptional<T> getCapability(
            Capability<T> cap,
            @Nullable Direction side
    ) {

        if (!(this.level instanceof ServerLevel lvl)) {
            return LazyOptional.empty();
        }

        if (side == null) {
            return super.getCapability(cap, null);
        }

        BlockEntity be = lvl.getBlockEntity(this.getBlockPos().offset(side.getOpposite().getNormal()));
        if (be == null) {
            return LazyOptional.empty();
        }

        return be.getCapability(cap, side);
    }


{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
    @Override
    public ModelData getModelData() {

        if (getFacadeData() != null) {

            return ModelData.builder()
                    .with(IFacadeBlockEntity.FACADE_BLOCK_STATE_MODEL_PROPERTY, getFacadeData().facadeBlockState())
                    .with(FACADE_DIRECTION, getFacadeData().facadeDirection())
                    .build();
        }
        return ModelData.EMPTY;
    }

}
