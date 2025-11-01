package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.facade.FacadeData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

public interface IFacadeBlockEntity {
    ModelProperty<BlockState> FACADE_BLOCK_STATE_MODEL_PROPERTY = new ModelProperty<>();

    void updateFacadeData(FacadeData newFacadeData);

    @Nullable FacadeData getFacadeData();

}
