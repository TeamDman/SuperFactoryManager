package ca.teamdman.sfm.common.tutorial.lobby;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class SFMTutorialLobby {
    private final LobbyId id;
    private final BlockPos roomCenter;
    private final BlockPos chamberOrigin;
    private final LinkedHashSet<UUID> playerIds;
    private ResourceLocation currentChamberId;

    public SFMTutorialLobby(
            LobbyId id,
            BlockPos roomCenter,
            BlockPos chamberOrigin,
            ResourceLocation currentChamberId
    ) {
        this.id = id;
        this.roomCenter = roomCenter;
        this.chamberOrigin = chamberOrigin;
        this.currentChamberId = currentChamberId;
        this.playerIds = new LinkedHashSet<>();
    }

    public LobbyId id() {
        return id;
    }

    public BlockPos roomCenter() {
        return roomCenter;
    }

    public BlockPos chamberOrigin() {
        return chamberOrigin;
    }

    public ResourceLocation currentChamberId() {
        return currentChamberId;
    }

    public void setCurrentChamberId(ResourceLocation currentChamberId) {
        this.currentChamberId = currentChamberId;
    }

    public Set<UUID> playerIds() {
        return playerIds;
    }
}