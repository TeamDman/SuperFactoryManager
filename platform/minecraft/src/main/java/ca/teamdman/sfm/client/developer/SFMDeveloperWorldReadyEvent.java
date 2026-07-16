package ca.teamdman.sfm.client.developer;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;

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
