package ca.teamdman.sfm.common.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public class SFMEntityUtils {
    @MCVersionDependentBehaviour
    public static ServerLevel getLevel(ServerPlayer player) {
{% if targets.mc_1_19_2 %}
        return player.getLevel();
{% elsif targets.mc_1_19_4 %}
        return player.getLevel();
{% elsif targets.mc_26_1_2 %}
        return player.level();
{% else %}
        return player.serverLevel();
{% endif %}
    }

    @MCVersionDependentBehaviour
    public static Level getLevel(Entity entity) {
{% if targets.mc_1_19_2 %}
        return entity.level;
{% elsif targets.mc_1_19_4 %}
        return entity.level;
{% else %}
        return entity.level();
{% endif %}
    }
}
