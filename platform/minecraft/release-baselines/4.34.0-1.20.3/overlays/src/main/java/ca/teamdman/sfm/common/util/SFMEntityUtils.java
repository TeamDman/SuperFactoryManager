package ca.teamdman.sfm.common.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public class SFMEntityUtils {
    @MCVersionDependentBehaviour
    public static ServerLevel getLevel(ServerPlayer player) {
        return player.serverLevel();
    }

    @MCVersionDependentBehaviour
    public static Level getLevel(Entity entity) {
        return entity.level();
    }
}
