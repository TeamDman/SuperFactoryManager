package ca.teamdman.sfm.common.util;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class SFMPlayerUtils {
    @MCVersionDependentBehaviour
    public static Level getLevel(Player player) {
        return player.level;
    }
}
