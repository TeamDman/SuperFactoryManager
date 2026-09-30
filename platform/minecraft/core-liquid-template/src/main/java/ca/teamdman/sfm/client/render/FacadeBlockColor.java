package ca.teamdman.sfm.client.render;

import ca.teamdman.sfm.common.blockentity.IFacadeBlockEntity;
import ca.teamdman.sfm.common.facade.FacadeData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class FacadeBlockColor implements BlockColor {

    @Override
    public int getColor(
            BlockState blockState,
            @Nullable BlockAndTintGetter blockAndTintGetter,
            @Nullable BlockPos blockPos,
            int tintIndex
    ) {
        if (blockAndTintGetter == null || blockPos == null) return -1;

        BlockEntity blockEntity = blockAndTintGetter.getBlockEntity(blockPos);
        if (!(blockEntity instanceof IFacadeBlockEntity facadeBlockEntity)) return -1;
        FacadeData facadeData = facadeBlockEntity.getFacadeData();
        if (facadeData == null) return -1;
        BlockState facadeState = facadeData.facadeBlockState();
        return Minecraft.getInstance().getBlockColors().getColor(facadeState, blockAndTintGetter, blockPos, tintIndex);
    }
}
