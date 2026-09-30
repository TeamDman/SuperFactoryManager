package ca.teamdman.sfm.common.util;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

public class SFMContainerUtil {
    public static boolean stillValid(BlockEntity blockEntity, Player player) {
        var level = blockEntity.getLevel();
        if (level == null) return false;
        var pos   = blockEntity.getBlockPos();
        if (level.getBlockEntity(pos) != blockEntity) return false;
        double dist = player.distanceToSqr(
                (double) pos.getX() + 0.5D,
                (double) pos.getY() + 0.5D,
                (double) pos.getZ() + 0.5D
        );
        return dist <= 64.0D;
    }
}
