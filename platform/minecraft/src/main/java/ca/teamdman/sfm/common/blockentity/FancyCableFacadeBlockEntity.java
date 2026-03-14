package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.registry.SFMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;

public class FancyCableFacadeBlockEntity extends CommonFacadeBlockEntity {
    public static final ModelProperty<Direction> FACADE_DIRECTION = new ModelProperty<>();

    public FancyCableFacadeBlockEntity(
            BlockPos pos,
            BlockState state
    ) {
        super(SFMBlockEntities.FANCY_CABLE_FACADE_BLOCK_ENTITY.get(), pos, state);
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
