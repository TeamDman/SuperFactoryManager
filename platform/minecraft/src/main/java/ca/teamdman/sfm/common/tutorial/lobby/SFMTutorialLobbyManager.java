package ca.teamdman.sfm.common.tutorial.lobby;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class SFMTutorialLobbyManager {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    private static final Map<LobbyId, SFMTutorialLobby> LOBBIES_BY_ID = new HashMap<>();
    private static final Map<UUID, LobbyId> PLAYER_TO_LOBBY = new HashMap<>();

    private SFMTutorialLobbyManager() {
    }

    public static SFMTutorialLobby createLobby(
            ServerPlayer player,
            BlockPos roomCenter,
            BlockPos chamberOrigin,
            ResourceLocation startingChamberId
    ) {
        removePlayerFromCurrentLobby(player.getUUID());

        LobbyId lobbyId = new LobbyId(NEXT_ID.getAndIncrement());
        SFMTutorialLobby lobby = new SFMTutorialLobby(
                lobbyId,
                roomCenter,
                chamberOrigin,
                startingChamberId
        );
        lobby.playerIds().add(player.getUUID());

        LOBBIES_BY_ID.put(lobbyId, lobby);
        PLAYER_TO_LOBBY.put(player.getUUID(), lobbyId);
        return lobby;
    }

    public static Optional<SFMTutorialLobby> getLobby(LobbyId lobbyId) {
        return Optional.ofNullable(LOBBIES_BY_ID.get(lobbyId));
    }

    public static Collection<SFMTutorialLobby> getLobbies() {
        ArrayList<SFMTutorialLobby> rtn = new ArrayList<>(LOBBIES_BY_ID.values());
        rtn.sort(Comparator.comparingInt(lobby -> lobby.id().value()));
        return rtn;
    }

    public static void assignPlayerToLobby(ServerPlayer player, LobbyId lobbyId) {
        removePlayerFromCurrentLobby(player.getUUID());
        SFMTutorialLobby lobby = LOBBIES_BY_ID.get(lobbyId);
        if (lobby != null) {
            lobby.playerIds().add(player.getUUID());
            PLAYER_TO_LOBBY.put(player.getUUID(), lobbyId);
        }
    }

    public static void removePlayerFromCurrentLobby(UUID playerId) {
        LobbyId priorLobbyId = PLAYER_TO_LOBBY.remove(playerId);
        if (priorLobbyId == null) {
            return;
        }
        SFMTutorialLobby priorLobby = LOBBIES_BY_ID.get(priorLobbyId);
        if (priorLobby == null) {
            return;
        }

        priorLobby.playerIds().remove(playerId);
        if (priorLobby.playerIds().isEmpty()) {
            LOBBIES_BY_ID.remove(priorLobbyId);
        }
    }
}