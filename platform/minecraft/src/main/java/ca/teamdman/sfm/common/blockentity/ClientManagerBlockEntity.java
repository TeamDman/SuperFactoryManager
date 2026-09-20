package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.net.SFMServerClientManagerSigningTransport;
import ca.teamdman.sfm.common.program.signature.*;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/** Holds a client visual program; it never creates a server ProgramContext or server ticker. */
public final class ClientManagerBlockEntity extends BlockEntity {
    private static final String DISK_TAG = "disk";
    private static final String WORLD_ID_TAG = "world_id";
    public static final String SIGNING_TAG = "client_program_signing";
    private ItemStack disk = ItemStack.EMPTY;
    private @Nullable UUID worldId;
    private long clientSnapshotRevision;
    private @Nullable ClientManagerSigningState signingState;

    public ClientManagerBlockEntity(BlockPos pos, BlockState state) {
        super(SFMBlockEntities.CLIENT_MANAGER.get(), pos, state);
    }

    public ItemStack disk() {
        return disk.copy();
    }

    public String storedSource() {
        return disk.isEmpty() ? "" : DiskItem.getProgramStringReadOnly(disk);
    }

    public List<ProgramAttestation> attestations() {
        var snapshot = signingSnapshot();
        return snapshot == null ? List.of() : snapshot.history();
    }

    public @Nullable UUID signingIncarnation() {
        var snapshot = signingSnapshot();
        return snapshot == null ? null : snapshot.incarnation();
    }

    public long signingRevision() {
        var snapshot = signingSnapshot();
        return snapshot == null ? -1 : snapshot.revision();
    }

    public @Nullable ClientManagerSigningSnapshot signingSnapshot() {
        return signingState == null ? null : signingState.snapshot();
    }

    public LabelPositionHolder labels() {
        return disk.isEmpty() ? LabelPositionHolder.empty() : LabelPositionHolder.fromReadOnly(disk);
    }

    public Set<String> referencedLabels() {
        return labels().labels().keySet();
    }

    public @Nullable UUID worldId() {
        return worldId;
    }

    /** In-memory revision used to avoid recompiling the same synced disk every frame. */
    public long clientSnapshotRevision() {
        return clientSnapshotRevision;
    }

    public void setDisk(ItemStack value) {
        if (!value.isEmpty() && value.getItem() != SFMItems.DISK.get()) {
            throw new IllegalArgumentException("Client Manager accepts only an SFM disk");
        }
        if (!sourceFitsSyncBudget(value)) {
            throw new IllegalArgumentException("Client Manager program exceeds its sync budget");
        }
        ItemStack replacement = value.copy();
        ClientManagerSigningBody body = signingBody(replacement);
        if (!replacement.isEmpty()) {
            replacement.setCount(1);
            replacement.getOrCreateTag().putString("sfm:program", body.source());
        }
        if (signingState == null) signingState = new ClientManagerSigningState(UUID.randomUUID(), body);
        else signingState.replaceBody(body);
        invalidateSigningChallenges();
        disk = replacement;
        clientSnapshotRevision++;
        changedAndSync();
    }

    public ItemStack removeDisk() {
        ItemStack removed = disk;
        if (signingState == null) signingState = new ClientManagerSigningState(UUID.randomUUID(), emptyBody());
        else signingState.replaceBody(emptyBody());
        invalidateSigningChallenges();
        disk = ItemStack.EMPTY;
        clientSnapshotRevision++;
        changedAndSync();
        return removed;
    }

    /** Called only after the server transport's authorization and aggregate admission checks. */
    public ClientManagerSigningState.Review reviewForSigning(ClientManagerSigningSession session, UUID player,
                                                              Collection<ResourceLocation> capabilities, long tick) {
        if (!serverSigningAvailable()) return unavailableReview();
        return signingState.review(session, player, capabilities, tick, signingAddress());
    }

    /** The acknowledgement is issued from the exact source committed below on the same server thread. */
    public ClientManagerSigningState.Review saveForSigning(ClientManagerSigningSession session, UUID player,
                                                            UUID incarnation, long revision, String source,
                                                            Collection<ResourceLocation> capabilities, long tick) {
        if (!serverSigningAvailable()) return unavailableReview();
        var review = signingState.save(session, player, incarnation, revision, source, capabilities, tick, signingAddress());
        if (review.status() == ClientManagerSigningState.Status.SAVED) {
            disk.getOrCreateTag().putString("sfm:program", signingState.snapshot().body().source());
            clientSnapshotRevision++;
            changedAndSync();
        }
        return review;
    }

    public ClientManagerSigningState.Status submitSignature(ClientManagerSigningSession session, UUID player,
                                                              UUID incarnation, long revision, UUID challenge,
                                                              byte[] attestation, long tick) {
        if (!serverSigningAvailable()) return ClientManagerSigningState.Status.UNAVAILABLE;
        var result = signingState.submitEncoded(session, player, incarnation, revision, challenge, attestation,
                tick, signingAddress());
        if (result == ClientManagerSigningState.Status.SIGNED) {
            // Metadata changes invalidate the client compiler/trust cache, not the acknowledged body revision.
            clientSnapshotRevision++;
            changedAndSync();
        }
        return result;
    }

    private boolean serverSigningAvailable() {
        return level != null && !level.isClientSide() && !isRemoved() && !disk.isEmpty() && signingState != null;
    }

    private static ClientManagerSigningState.Review unavailableReview() {
        return new ClientManagerSigningState.Review(ClientManagerSigningState.Status.UNAVAILABLE, Optional.empty());
    }

    private String signingAddress() {
        return Objects.requireNonNull(level).dimension().location() + "/" + worldPosition.asLong();
    }

    private void invalidateSigningChallenges() {
        if (level instanceof ServerLevel server && signingState != null) {
            SFMServerClientManagerSigningTransport.invalidate(server.getServer(), signingState.snapshot().incarnation());
        }
    }

    public void changedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null) return;
        if (level.isClientSide()) {
            ClientManagerLoadedRegistry.add(level, this);
        } else if (level instanceof ServerLevel serverLevel) {
            // Old saves acquire an incarnation durably even when the disk is never edited again.
            setChanged();
            UUID persisted = ClientManagerWorldIdentitySavedData.forLevel(serverLevel);
            if (!persisted.equals(worldId)) {
                worldId = persisted;
                clientSnapshotRevision++;
                changedAndSync();
            }
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && level.isClientSide()) ClientManagerLoadedRegistry.remove(level, this);
        invalidateSigningChallenges();
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!disk.isEmpty()) tag.put(DISK_TAG, disk.save(new CompoundTag()));
        if (worldId != null) tag.putUUID(WORLD_ID_TAG, worldId);
        if (signingState != null) tag.putByteArray(SIGNING_TAG,
                ClientManagerSigningCodec.encodeMetadata(ClientManagerSigningMetadata.from(signingState.snapshot())));
    }

    @Override
    public void load(CompoundTag tag) {
        if (level != null && level.isClientSide()) {
            handleUpdateTag(tag);
            return;
        }
        super.load(tag);
        ItemStack restored = tag.contains(DISK_TAG) ? ItemStack.of(tag.getCompound(DISK_TAG)) : ItemStack.EMPTY;
        disk = restored.getItem() == SFMItems.DISK.get() ? restored : ItemStack.EMPTY;
        if (!disk.isEmpty()) disk.setCount(1);
        signingState = null;
        try {
            ClientManagerSigningBody body = signingBody(disk);
            if (!disk.isEmpty()) disk.getOrCreateTag().putString("sfm:program", body.source());
            if (tag.contains(SIGNING_TAG, Tag.TAG_BYTE_ARRAY)) {
                try {
                    signingState = ClientManagerSigningCodec.decodeMetadata(tag.getByteArray(SIGNING_TAG))
                            .restoreServerState(body);
                } catch (IllegalArgumentException malformed) {
                    // Corrupt public metadata never preserves old authority or a reusable challenge identity.
                }
            }
            if (signingState == null) signingState = new ClientManagerSigningState(UUID.randomUUID(), body);
        } catch (IllegalArgumentException oversizedLegacyDisk) {
            // Preserve the full disk on the server, but publish no executable/signable projection.
        }
        worldId = tag.hasUUID(WORLD_ID_TAG) ? tag.getUUID(WORLD_ID_TAG) : null;
        clientSnapshotRevision++;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag update = new CompoundTag();
        if (worldId != null) update.putUUID(WORLD_ID_TAG, worldId);
        // Preserve the full disk server-side. Arbitrary item data and oversized
        // legacy labels never become a client execution input or network payload.
        if (!disk.isEmpty()) ClientManagerProgramProjection.project(disk.getTag() == null
                ? new CompoundTag() : disk.getTag()).ifPresent(projection -> {
            ItemStack projectedDisk = new ItemStack(SFMItems.DISK.get());
            projectedDisk.setTag(projection);
            update.put(DISK_TAG, projectedDisk.save(new CompoundTag()));
        });
        if (signingState != null && update.contains(DISK_TAG)) {
            update.putByteArray(SIGNING_TAG,
                    ClientManagerSigningCodec.encodeMetadata(ClientManagerSigningMetadata.from(signingState.snapshot())));
        }
        return update;
    }

    private static boolean sourceFitsSyncBudget(ItemStack candidate) {
        if (candidate.isEmpty()) return true;
        return ClientManagerProgramProjection.project(candidate.getTag() == null
                ? new CompoundTag() : candidate.getTag()).isPresent();
    }

    private static ClientManagerSigningBody emptyBody() {
        return new ClientManagerSigningBody("", Map.of());
    }

    private static ClientManagerSigningBody signingBody(ItemStack candidate) {
        return candidate.isEmpty() ? emptyBody() : ClientManagerSigningBody.fromDiskTag(
                candidate.getTag() == null ? new CompoundTag() : candidate.getTag());
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        if (tag != null) handleUpdateTag(tag);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        // A remote server is not trusted to have used our bounded sender.
        CompoundTag projected = new CompoundTag();
        if (tag.hasUUID(WORLD_ID_TAG)) projected.putUUID(WORLD_ID_TAG, tag.getUUID(WORLD_ID_TAG));
        CompoundTag item = tag.getCompound(DISK_TAG);
        if (item.getString("id").equals("sfm:disk")) {
            ClientManagerProgramProjection.project(item.getCompound("tag")).ifPresent(program -> {
                CompoundTag projectedItem = new CompoundTag();
                projectedItem.putString("id", "sfm:disk");
                projectedItem.putByte("Count", (byte) 1);
                projectedItem.put("tag", program);
                projected.put(DISK_TAG, projectedItem);
            });
        }
        // Do not use server restore semantics here: a mismatched projection must not repair/advance
        // purported server evidence or generate a client-side incarnation.
        disk = projected.contains(DISK_TAG) ? ItemStack.of(projected.getCompound(DISK_TAG)) : ItemStack.EMPTY;
        worldId = projected.hasUUID(WORLD_ID_TAG) ? projected.getUUID(WORLD_ID_TAG) : null;
        signingState = null;
        if (!disk.isEmpty() && tag.contains(SIGNING_TAG, Tag.TAG_BYTE_ARRAY)) {
            try {
                var body = signingBody(disk);
                var metadata = ClientManagerSigningCodec.decodeMetadata(tag.getByteArray(SIGNING_TAG));
                if (metadata.matchesBody(body)) {
                    signingState = new ClientManagerSigningState(metadata.incarnation(), metadata.revision(), body, metadata.history());
                }
            } catch (IllegalArgumentException invalidMetadata) {
                // The program may still be reviewed for exact-source consent, but has no signer authority.
            }
        }
        clientSnapshotRevision++;
    }
}
