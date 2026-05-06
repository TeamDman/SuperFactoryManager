package ca.teamdman.sfm.client.render;

import ca.teamdman.sfm.common.blockentity.IFacadeBlockEntity;
import ca.teamdman.sfm.common.facade.FacadeData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class FacadeBlockTintSource implements BlockTintSource {

    @Override
    public int color(BlockState blockState) {
        return -1;
    }

    @Override
    public int colorInWorld(
            BlockState blockState,
            BlockAndTintGetter blockAndTintGetter,
            BlockPos blockPos
    ) {
        BlockEntity blockEntity = blockAndTintGetter.getBlockEntity(blockPos);
        if (!(blockEntity instanceof IFacadeBlockEntity facadeBlockEntity)) return -1;
        FacadeData facadeData = facadeBlockEntity.getFacadeData();
        if (facadeData == null) return -1;
        BlockState facadeState = facadeData.facadeBlockState();
        BlockTintSource blockTintSource = Minecraft.getInstance().getBlockColors().getTintSource(facadeState, 0);
        if (blockTintSource == null) return -1;
        
        return blockTintSource.colorInWorld(facadeState, blockAndTintGetter, blockPos);
    }
}