package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/** Holds a client visual program; it never creates a server ProgramContext or server ticker. */
public final class ClientManagerBlockEntity extends BlockEntity {
    private static final String DISK_TAG = "disk";
    private static final String WORLD_ID_TAG = "world_id";
    private ItemStack disk = ItemStack.EMPTY;
    private @Nullable UUID worldId;
    private long clientSnapshotRevision;

    public ClientManagerBlockEntity(BlockPos pos, BlockState state) {
        super(SFMBlockEntities.CLIENT_MANAGER.get(), pos, state);
    }

    public ItemStack disk() {
        return disk.copy();
    }

    public String storedSource() {
        return disk.isEmpty() ? "" : DiskItem.getProgramStringReadOnly(disk);
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
        disk = value.copy();
        if (!disk.isEmpty()) disk.setCount(1);
        clientSnapshotRevision++;
        changedAndSync();
    }

    public ItemStack removeDisk() {
        ItemStack removed = disk;
        disk = ItemStack.EMPTY;
        clientSnapshotRevision++;
        changedAndSync();
        return removed;
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
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!disk.isEmpty()) tag.put(DISK_TAG, disk.save(new CompoundTag()));
        if (worldId != null) tag.putUUID(WORLD_ID_TAG, worldId);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ItemStack restored = tag.contains(DISK_TAG) ? ItemStack.of(tag.getCompound(DISK_TAG)) : ItemStack.EMPTY;
        disk = restored.getItem() == SFMItems.DISK.get() ? restored : ItemStack.EMPTY;
        if (!disk.isEmpty()) disk.setCount(1);
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
        return update;
    }

    private static boolean sourceFitsSyncBudget(ItemStack candidate) {
        if (candidate.isEmpty()) return true;
        return ClientManagerProgramProjection.project(candidate.getTag() == null
                ? new CompoundTag() : candidate.getTag()).isPresent();
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
        load(projected);
    }
}
