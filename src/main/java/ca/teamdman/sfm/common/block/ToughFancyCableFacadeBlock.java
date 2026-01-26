package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.registry.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class ToughFancyCableFacadeBlock extends FancyCableFacadeBlock implements IFacadableBlock, net.minecraft.world.level.block.EntityBlock {
    public ToughFancyCableFacadeBlock(Properties properties) {
        super(properties);
        registerDefaultState(
                defaultBlockState()
                        .setValue(ca.teamdman.sfm.common.facade.FacadeTransparency.FACADE_TRANSPARENCY_PROPERTY, ca.teamdman.sfm.common.facade.FacadeTransparency.TRANSLUCENT)
                        .setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 0)
        );
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(
            BlockPos blockPos,
            BlockState blockState
    ) {
        return SFMBlockEntities.TOUGH_FANCY_CABLE_FACADE_BLOCK_ENTITY.get().create(blockPos, blockState);
    }

    @Override
    public ItemStack getCloneItemStack(
            BlockGetter pLevel,
            BlockPos pPos,
            BlockState pState
    ) {
        return new ItemStack(SFMBlocks.TOUGH_FANCY_CABLE_BLOCK.get());
    }

    @Override
    public IFacadableBlock getNonFacadeBlock() {
        return SFMBlocks.TOUGH_FANCY_CABLE_BLOCK.get();
    }

    @Override
    public IFacadableBlock getFacadeBlock() {
        return SFMBlocks.TOUGH_FANCY_CABLE_FACADE_BLOCK.get();
    }

    // TODO: implement destroyTime to inherit from facade block state

    @Override
    @SuppressWarnings("deprecation")
    public float getExplosionResistance(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.BlockGetter world, net.minecraft.core.BlockPos pos, net.minecraft.world.level.Explosion explosion) {
        try {
            net.minecraft.world.level.block.entity.BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof ca.teamdman.sfm.common.blockentity.IFacadeBlockEntity facadeBE) {
                ca.teamdman.sfm.common.facade.FacadeData fd = facadeBE.getFacadeData();
                if (fd != null) {
                    return fd.facadeBlockState().getBlock().getExplosionResistance();
                }
            }
        } catch (Throwable ignored) {
        }
        return super.getExplosionResistance();
    }
}
