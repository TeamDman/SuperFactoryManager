package ca.teamdman.sfm.common.net.multiplayer;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/** One world-owned policy shared by every dimension and live transport; never loaded from a client file. */
public final class SFMMultiplayerPacketPolicySavedData extends SavedData {
    public static final String STORAGE_KEY = "sfm_multiplayer_packet_policy";
    private final SFMMultiplayerPacketPolicy policy = new SFMMultiplayerPacketPolicy();
    private boolean quarantined;
    private String diagnostic = "";

    public SFMMultiplayerPacketPolicySavedData() { setDirty(); }

    public static SFMMultiplayerPacketPolicySavedData load(CompoundTag tag) {
        var data = new SFMMultiplayerPacketPolicySavedData();
        data.setDirty(false);
        try {
            if (!tag.getAllKeys().equals(Set.of("schema", "grants"))
                    || !tag.contains("schema", Tag.TAG_INT) || tag.getInt("schema") != SFMMultiplayerPacketPolicyCodec.VERSION
                    || !tag.contains("grants", Tag.TAG_BYTE_ARRAY)) throw new IllegalArgumentException("Unsupported policy envelope");
            for (var grant : SFMMultiplayerPacketPolicyCodec.decode(tag.getByteArray("grants"))) data.policy.grant(grant);
        } catch (RuntimeException malformed) {
            // decode is all-or-nothing; this loop is defensive if a future policy constructor adds validation.
            data.policy.snapshot().forEach(grant -> data.policy.revoke(grant.id()));
            data.quarantined = true;
            data.diagnostic = "Stored packet policy was rejected. All remote operations are denied; an owner must explicitly recover_empty.";
        }
        return data;
    }

    @MCVersionDependentBehaviour
    public static SFMMultiplayerPacketPolicySavedData forServer(MinecraftServer server) {
        Objects.requireNonNull(server);
        if (!server.isSameThread()) throw new IllegalStateException("Packet policy requires the server thread");
        return server.overworld().getDataStorage().computeIfAbsent(SFMMultiplayerPacketPolicySavedData::load,
                SFMMultiplayerPacketPolicySavedData::new, STORAGE_KEY);
    }

    /** Canonical live authority object. Operator mutations must use this owner's grant/revoke methods. */
    public SFMMultiplayerPacketPolicy policy() { return policy; }
    public synchronized boolean quarantined() { return quarantined; }
    public synchronized String diagnostic() { return diagnostic; }

    /** Grant IDs are immutable: changing any scope requires an explicit revoke followed by a new grant. */
    public synchronized void grant(SFMMultiplayerPacketPolicy.Grant grant) {
        requireAvailable();
        Objects.requireNonNull(grant);
        var next = new ArrayList<>(policy.snapshot());
        if (next.stream().anyMatch(existing -> existing.id().equals(grant.id()))) {
            throw new IllegalArgumentException("Grant ID already exists; revoke it explicitly instead of replacing authority");
        }
        next.add(grant);
        SFMMultiplayerPacketPolicyCodec.encode(next);
        policy.grant(grant);
        setDirty();
    }

    public synchronized boolean revoke(UUID id) {
        requireAvailable();
        boolean changed = policy.revoke(id);
        if (changed) setDirty();
        return changed;
    }

    /** Explicit operator recovery only; never clears a valid policy as a side effect of reading it. */
    public synchronized boolean recoverEmpty() {
        if (!quarantined) return false;
        policy.snapshot().forEach(grant -> policy.revoke(grant.id()));
        quarantined = false;
        diagnostic = "";
        setDirty();
        return true;
    }

    private void requireAvailable() {
        if (quarantined) throw new IllegalStateException(diagnostic);
    }

    @Override public synchronized CompoundTag save(CompoundTag tag) {
        requireAvailable();
        tag.putInt("schema", SFMMultiplayerPacketPolicyCodec.VERSION);
        tag.putByteArray("grants", SFMMultiplayerPacketPolicyCodec.encode(policy.snapshot()));
        return tag;
    }
}
