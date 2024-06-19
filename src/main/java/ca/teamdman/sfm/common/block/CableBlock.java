package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.cablenetwork.CableNetworkManager;
import ca.teamdman.sfm.common.cablenetwork.ICableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

public class CableBlock extends Block implements ICableBlock {

    public CableBlock() {
        super(Block.Properties.of()
                      .instrument(NoteBlockInstrument.BASS)
                      .destroyTime(1f)
                      .sound(SoundType.METAL));
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
        CableNetworkManager.onCablePlaced(world, pos);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        CableNetworkManager.onCableRemoved(level, pos);
    }
}
