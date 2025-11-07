package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.facade.FacadeData;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraftforge.common.property.IUnlistedProperty;
import org.jetbrains.annotations.Nullable;

public interface IFacadeBlockEntity {

    IUnlistedProperty<IBlockState> FACADE_BLOCK_STATE_MODEL_PROPERTY = new IUnlistedProperty<>();

    void updateFacadeData(FacadeData newFacadeData);

    @Nullable FacadeData getFacadeData();

}
