package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/**
 * Central policy for packet-computation effects in the single-player MVP.
 *
 * <p>The boolean form is deliberately Minecraft-independent so every state
 * transition, including opening a world to LAN, has a focused boundary test.</p>
 */
public final class SFMPacketEffectGate {
    private SFMPacketEffectGate() {
    }

    public static boolean allowsPrivateIntegratedWorld(
            boolean singleplayer,
            boolean published,
            boolean localPlayerOwnsWorld
    ) {
        return singleplayer && !published && localPlayerOwnsWorld;
    }

    @MCVersionDependentBehaviour
    public static boolean allowsServerEffects(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        MinecraftServer server = player.getServer();
        return server != null && allowsPrivateIntegratedWorld(
                server.isSingleplayer(),
                server.isPublished(),
                server.isSingleplayerOwner(player.getGameProfile())
        );
    }
}
