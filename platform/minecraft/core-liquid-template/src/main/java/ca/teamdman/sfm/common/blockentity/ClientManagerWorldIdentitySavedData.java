package ca.teamdman.sfm.common.blockentity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.UUID;

/** Stable world identity for exact client-program consent; a cloned world retains its identity. */
public final class ClientManagerWorldIdentitySavedData extends SavedData {
    private static final String STORAGE_KEY = "sfm_client_program_world_identity";
    private static final String UUID_KEY = "world_uuid";
    private final UUID worldId;

    private ClientManagerWorldIdentitySavedData() {
        worldId = UUID.randomUUID();
        setDirty();
    }

    private ClientManagerWorldIdentitySavedData(CompoundTag tag) {
        if (tag.hasUUID(UUID_KEY)) {
            worldId = tag.getUUID(UUID_KEY);
        } else {
            worldId = UUID.randomUUID();
            setDirty();
        }
    }

    public static UUID forLevel(ServerLevel level) {
        // A single identity is shared by all dimensions of this saved world.
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(ClientManagerWorldIdentitySavedData::new,
                        ClientManagerWorldIdentitySavedData::new, STORAGE_KEY)
                .worldId;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putUUID(UUID_KEY, worldId);
        return tag;
    }
}
