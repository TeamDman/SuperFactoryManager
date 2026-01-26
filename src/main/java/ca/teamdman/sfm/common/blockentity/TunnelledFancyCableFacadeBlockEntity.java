package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.registry.SFMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.Nullable;

import static ca.teamdman.sfm.common.blockentity.FancyCableFacadeBlockEntity.FACADE_DIRECTION;

public class TunnelledFancyCableFacadeBlockEntity extends CommonFacadeBlockEntity {
    public TunnelledFancyCableFacadeBlockEntity(
            BlockPos blockPos,
            BlockState blockState
    ) {

        super(SFMBlockEntities.TUNNELLED_FANCY_CABLE_FACADE_BLOCK_ENTITY.get(), blockPos, blockState);
    }

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
