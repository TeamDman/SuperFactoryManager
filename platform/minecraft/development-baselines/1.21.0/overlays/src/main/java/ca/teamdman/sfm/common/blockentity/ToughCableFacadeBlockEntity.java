package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

public class ToughCableFacadeBlockEntity extends CommonFacadeBlockEntity {
    public ToughCableFacadeBlockEntity(
            BlockPos pos,
            BlockState state
    ) {

        super(SFMBlockEntities.TOUGH_CABLE_FACADE.get(), pos, state);
    }

    @Override
    public ModelData getModelData() {

        if (getFacadeData() != null) {
            return ModelData
                    .builder()
                    .with(FACADE_BLOCK_STATE_MODEL_PROPERTY, getFacadeData().facadeBlockState())
                    .build();
        }
        return ModelData.EMPTY;
    }

}
