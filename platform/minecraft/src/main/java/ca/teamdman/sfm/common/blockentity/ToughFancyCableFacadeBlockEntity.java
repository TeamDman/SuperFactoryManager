package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

import static ca.teamdman.sfm.common.blockentity.FancyCableFacadeBlockEntity.FACADE_DIRECTION;

public class ToughFancyCableFacadeBlockEntity extends CommonFacadeBlockEntity {
    public ToughFancyCableFacadeBlockEntity(
            BlockPos pos,
            BlockState state
    ) {

        super(SFMBlockEntities.TOUGH_FANCY_CABLE_FACADE.get(), pos, state);
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
