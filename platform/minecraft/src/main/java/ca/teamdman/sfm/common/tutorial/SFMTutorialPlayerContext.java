package ca.teamdman.sfm.common.tutorial;

import ca.teamdman.sfm.common.net.ClientboundTutorialContextPacket;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.tutorial.lobby.SFMTutorialLobby;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public final class SFMTutorialPlayerContext {
    private static final String HAS_CONTEXT_KEY = "sfm:tutorial:has_context";
    private static final String ORIGIN_X_KEY = "sfm:tutorial:origin_x";
    private static final String ORIGIN_Y_KEY = "sfm:tutorial:origin_y";
    private static final String ORIGIN_Z_KEY = "sfm:tutorial:origin_z";
    private static final String CHAMBER_ID_KEY = "sfm:tutorial:chamber_id";

    private SFMTutorialPlayerContext() {
    }

    public static void rememberPlayerLobby(
            ServerPlayer player,
            SFMTutorialLobby lobby
    ) {
        player.getPersistentData().putBoolean(HAS_CONTEXT_KEY, true);
        player.getPersistentData().putInt(ORIGIN_X_KEY, lobby.chamberOrigin().getX());
        player.getPersistentData().putInt(ORIGIN_Y_KEY, lobby.chamberOrigin().getY());
        player.getPersistentData().putInt(ORIGIN_Z_KEY, lobby.chamberOrigin().getZ());
        player.getPersistentData().putString(CHAMBER_ID_KEY, lobby.currentChamberId().toString());
        syncToClient(player);
    }

    public static void clear(ServerPlayer player) {
        player.getPersistentData().remove(HAS_CONTEXT_KEY);
        player.getPersistentData().remove(ORIGIN_X_KEY);
        player.getPersistentData().remove(ORIGIN_Y_KEY);
        player.getPersistentData().remove(ORIGIN_Z_KEY);
        player.getPersistentData().remove(CHAMBER_ID_KEY);
        syncToClient(player);
    }

    public static Optional<BlockPos> getChamberOrigin(Player player) {
        if (!player.getPersistentData().getBoolean(HAS_CONTEXT_KEY)) {
            return Optional.empty();
        }
        return Optional.of(new BlockPos(
                player.getPersistentData().getInt(ORIGIN_X_KEY),
                player.getPersistentData().getInt(ORIGIN_Y_KEY),
                player.getPersistentData().getInt(ORIGIN_Z_KEY)
        ));
    }

    public static @Nullable ResourceLocation getChamberId(Player player) {
        if (!player.getPersistentData().getBoolean(HAS_CONTEXT_KEY)) {
            return null;
        }
        String value = player.getPersistentData().getString(CHAMBER_ID_KEY);
        if (value.isBlank()) {
            return null;
        }
        return ResourceLocation.tryParse(value);
    }

    public static void syncToClient(ServerPlayer player) {
        Optional<BlockPos> origin = getChamberOrigin(player);
        if (origin.isEmpty()) {
            SFMPackets.sendToPlayer(player, new ClientboundTutorialContextPacket(false, null, null));
            return;
        }

        SFMPackets.sendToPlayer(
                player,
                new ClientboundTutorialContextPacket(true, origin.get(), getChamberId(player))
        );
    }
}
