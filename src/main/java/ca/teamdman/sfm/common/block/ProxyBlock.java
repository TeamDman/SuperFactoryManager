package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.blockentity.ProxyBlockEntity;
import ca.teamdman.sfm.common.containermenu.ProxyContainerMenu;
import ca.teamdman.sfm.common.registry.SFMBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.Nullable;

public class ProxyBlock extends BaseEntityBlock implements EntityBlock {
    public ProxyBlock() {
        super(Properties.of()
                .destroyTime(2)
                .sound(SoundType.METAL));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        throw new NotImplementedException("This isn't used until 1.20.5 apparently");
    }

    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return SFMBlockEntities.PROXY_BLOCK_ENTITY.get()
                .create(pPos, pState);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState pState,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult pHitResult
    ) {
        if (level.getBlockEntity(pos) instanceof ProxyBlockEntity pbe && player instanceof ServerPlayer sp) {
            sp.openMenu(pbe, buf -> ProxyContainerMenu.encode(pbe, buf));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof Container container) {
                Containers.dropContents(level, pos, container);
                level.updateNeighbourForOutputSignal(pos, this);
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}
