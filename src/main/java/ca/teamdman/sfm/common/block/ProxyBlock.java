package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.blockentity.ProxyBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class ProxyBlock extends BaseEntityBlock implements EntityBlock {
    public ProxyBlock() {
        super(Properties.of()
                .destroyTime(2)
                .sound(SoundType.METAL));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return null;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new ProxyBlockEntity(blockPos, blockState);
    }
}
