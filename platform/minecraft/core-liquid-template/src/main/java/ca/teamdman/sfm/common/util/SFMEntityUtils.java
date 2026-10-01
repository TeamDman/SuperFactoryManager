package ca.teamdman.sfm.common.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public class SFMEntityUtils {
    @MCVersionDependentBehaviour
    public static ServerLevel getLevel(ServerPlayer player) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        return player.getLevel();
{% when "26.1.2" %}
        return player.level();
{% else %}
        return player.serverLevel();
{% endcase %}
    }

    @MCVersionDependentBehaviour
    public static Level getLevel(Entity entity) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        return entity.level;
{% else %}
        return entity.level();
{% endcase %}
    }
}
