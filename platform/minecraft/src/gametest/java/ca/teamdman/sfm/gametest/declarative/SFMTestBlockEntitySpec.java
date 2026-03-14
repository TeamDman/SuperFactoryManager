package ca.teamdman.sfm.gametest.declarative;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public record SFMTestBlockEntitySpec<T extends BlockEntity>(
        String label,
        BlockPos posRelativeToManager,
        Block block,
        @Nullable Consumer<T> blockEntityConfigurer
) {
    public static <U extends BlockEntity> SFMTestBlockEntitySpec<U> of(
            String label,
            BlockPos posRelativeToManager,
            Block block,
            @Nullable Consumer<U> blockEntityConfigurer
    ) {
        return new SFMTestBlockEntitySpec<>(label, posRelativeToManager, block, blockEntityConfigurer);
    }

    public static SFMTestBlockEntitySpec<?> of(
            String label,
            BlockPos posRelativeToManager,
            Block block
    ) {
        return new SFMTestBlockEntitySpec<>(label, posRelativeToManager, block, null);
    }
}
