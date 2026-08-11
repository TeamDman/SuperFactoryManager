package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.block.BufferBlock;
import ca.teamdman.sfm.common.block.BufferBlockTier;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class BufferBlockEntity extends BlockEntity {
    private final BufferBlockEntityContents contents;

    public BufferBlockEntity(
            BlockPos pPos,
            BlockState pBlockState
    ) {
        super(SFMBlockEntities.BUFFER.get(), pPos, pBlockState);
        BufferBlockTier tier = pBlockState.getBlock() instanceof BufferBlock bufferBlock
                               ? bufferBlock.tier
                               : BufferBlockTier.Unit;
        this.contents = new BufferBlockEntityContents(tier);
    }

    public BufferBlockEntityContents getContents() {
        return contents;
    }


    public static void serverTick(
            @SuppressWarnings("unused") Level level,
            @SuppressWarnings("unused") BlockPos pos,
            @SuppressWarnings("unused") BlockState state,
            BufferBlockEntity bufferBlockEntity
    ) {
        if (bufferBlockEntity.getContents().lastUsedResource != state.getValue(BufferBlock.CONTAINED_RESOURCE)) {
            level.setBlock(pos,
                           state.setValue(
                                   BufferBlock.CONTAINED_RESOURCE,
                                   bufferBlockEntity.getContents().lastUsedResource
                           ),
                           Block.UPDATE_CLIENTS
            );
        }
    }
}
