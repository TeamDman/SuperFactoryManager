package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.facade.FacadeData;
import org.jetbrains.annotations.Nullable;

public interface IFacadeBlockEntity {
//    ModelProperty<IBlockState> FACADE_BLOCK_STATE_MODEL_PROPERTY = new UnlistedBlockP<>();

    void updateFacadeData(FacadeData newFacadeData);

    @Nullable FacadeData getFacadeData();

}
