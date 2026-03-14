package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.registry.SFMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

public class CableFacadeBlockEntity extends CommonFacadeBlockEntity {
    public CableFacadeBlockEntity(
            BlockPos pos,
            BlockState state
    ) {
        super(SFMBlockEntities.CABLE_FACADE_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public ModelData getModelData() {
        if (getFacadeData() != null) {
            return ModelData.builder().with(FACADE_BLOCK_STATE_MODEL_PROPERTY, getFacadeData().facadeBlockState()).build();
        }
        return ModelData.EMPTY;
    }

}
