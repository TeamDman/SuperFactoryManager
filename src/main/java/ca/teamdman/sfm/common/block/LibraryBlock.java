package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.blockentity.LibraryBlockEntity;
import ca.teamdman.sfm.common.cablenetwork.CableNetworkManager;
import ca.teamdman.sfm.common.cablenetwork.ICableBlock;
import ca.teamdman.sfm.common.registry.SFMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import org.jetbrains.annotations.Nullable;

/**
 * A block that stores SFML library definitions (protocols, structs, macros).
 * Can be referenced by manager programs via "use library" statements.
 */
public class LibraryBlock extends BaseEntityBlock implements EntityBlock, ICableBlock {

    public LibraryBlock() {
        super(BlockBehaviour.Properties
                .of(Material.PISTON)
                .destroyTime(1.5f)
                .sound(SoundType.METAL));
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return SFMBlockEntities.LIBRARY_BLOCK_ENTITY.get().create(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(
            BlockState state,
            Level world,
            BlockPos pos,
            BlockState oldState,
            boolean isMoving
    ) {
        CableNetworkManager.onCablePlaced(world, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState newState,
            boolean isMoving
    ) {
        if (!state.is(newState.getBlock())) {
            CableNetworkManager.onCableRemoved(level, pos);
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}
