package ca.teamdman.sfm.common.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

public class SFMEntityUtils {
    @MCVersionDependentBehaviour
    public static WorldServer getLevel(EntityPlayerMP player) {
        return player.getServerWorld();
    }

    @MCVersionDependentBehaviour
    public static World getLevel(Entity entity) {
        return entity.getEntityWorld();
    }
}
