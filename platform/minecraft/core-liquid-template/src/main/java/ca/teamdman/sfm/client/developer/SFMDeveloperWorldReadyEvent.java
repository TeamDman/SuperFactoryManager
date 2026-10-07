package ca.teamdman.sfm.client.developer;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.eventbus.api.Event;
{% else %}
import net.neoforged.bus.api.Event;
{% endcase %}

/**
 * Posted after an IDE-created developer world is ready for optional development tooling.
 * The main source owns world creation; optional source sets may subscribe to this event.
 */
public final class SFMDeveloperWorldReadyEvent extends Event {
    private final MinecraftServer server;
    private final ServerLevel level;

    public SFMDeveloperWorldReadyEvent(MinecraftServer server, ServerLevel level) {
        this.server = server;
        this.level = level;
    }

    public MinecraftServer server() {
        return server;
    }

    public ServerLevel level() {
        return level;
    }
}
