package ca.teamdman.sfm.common.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;

public class SFMContainerUtil {
    public static boolean stillValid(TileEntity blockEntity, EntityPlayer player) {
        var level = blockEntity.getWorld();
        if (level == null) return false;
        var pos   = blockEntity.getPos();
        if (level.getTileEntity(pos) != blockEntity) return false;
        double dist = player.getDistance(
                (double) pos.getX() + 0.5D,
                (double) pos.getY() + 0.5D,
                (double) pos.getZ() + 0.5D
        );
        return dist <= 64.0D;
    }
}
