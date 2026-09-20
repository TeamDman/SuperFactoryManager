package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.block.TouchDisplayBlock;
import ca.teamdman.sfm.common.capability.IImageHandler;
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.image.SFMImageSnapshot;
import ca.teamdman.sfm.common.image.SFMImageSnapshotCodec;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import ca.teamdman.sfm.common.resourcetype.SFMImageStack;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueJsonCodec;
import ca.teamdman.sfm.common.value.SFMTouchValue;
import net.minecraft.ResourceLocationException;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Server-owned display content, replicated as one immutable snapshot. */
public class TouchDisplayBlockEntity extends BlockEntity {
    private static final String CONTENT_TAG = "display_content";
    private static final String IMAGE_TAG = "image";
    private static final String STATE_TAG = "state";
    private static final String VALUE_CODEC_TAG = "value_codec";
    private static final String REVISION_TAG = "revision";
    private static final String SNAPSHOT_TAG = "snapshot";

    /** A bundled static placeholder; no client-side upload or image transport is needed for P2A. */
    public static final ResourceLocation DEFAULT_IMAGE = new ResourceLocation(
            "sfm", "textures/block/buffer_unknown.png"
    );
    public static final ResourceLocation RED_FIXTURE_IMAGE = new ResourceLocation(
            "minecraft", "textures/block/red_concrete.png"
    );
    public static final ResourceLocation BLUE_FIXTURE_IMAGE = new ResourceLocation(
            "minecraft", "textures/block/blue_concrete.png"
    );

    /** Image and interaction state always share the same server-assigned revision. */
    public record DisplayContent(
            ResourceLocation imageRef,
            @Nullable SFMImageSnapshot imageSnapshot,
            SFMValue state,
            long revision
    ) {
        public DisplayContent {
            Objects.requireNonNull(imageRef, "imageRef");
            Objects.requireNonNull(state, "state");
            if (revision < 0) {
                throw new IllegalArgumentException("Display content revision must be non-negative");
            }
            if (imageSnapshot != null && !imageRef.equals(snapshotLocation(imageSnapshot))) {
                throw new IllegalArgumentException("Image reference does not match snapshot digest");
            }
            SFMValueJsonCodec.encode(state);
        }

        public DisplayContent(ResourceLocation imageRef, SFMValue state, long revision) {
            this(imageRef, null, state, revision);
        }
    }

    private volatile DisplayContent content = new DisplayContent(DEFAULT_IMAGE, SFMValue.nullValue(), 0);
    private final IImageHandler imageSink = new IImageHandler() {
        @Override
        public SFMImageStack getImage() {
            // A display consumes an image; a previous image never fills its sink slot.
            return SFMImageStack.EMPTY;
        }

        @Override
        public SFMImageStack insertImage(SFMImageStack image, boolean simulate) {
            if (image.isEmpty() || level == null || level.isClientSide()) return image;
            try {
                if (content.revision() == Long.MAX_VALUE) return image;
                SFMTouchValue.requireCommitEnvelopeFits(
                        level.dimension().location(), worldPosition,
                        getBlockState().getValue(TouchDisplayBlock.FACING),
                        content.revision() + 1, image.interactionState()
                );
                if (!simulate) commitContent(image);
                return SFMImageStack.EMPTY;
            } catch (IllegalArgumentException | IllegalStateException rejected) {
                return image;
            }
        }

        @Override
        public SFMImageStack extractImage(boolean simulate) {
            return SFMImageStack.EMPTY;
        }

        @Override
        public boolean canExtract() {
            return false;
        }
    };
    private final LazyOptional<IImageHandler> imageSinkCapability = LazyOptional.of(() -> imageSink);

    public TouchDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(SFMBlockEntities.TOUCH_DISPLAY.get(), pos, state);
    }

    public DisplayContent content() {
        return content;
    }

    /**
     * Atomically replaces the display tuple on the logical server. Identical
     * content is a no-op, so readers can use the revision to detect changes.
     *
     * @return whether a new revision was committed
     */
    public boolean commitContent(ResourceLocation imageRef, SFMValue state) {
        return commitContent(imageRef, null, state);
    }

    /** Atomically commits one transferred image with its interaction state. */
    public boolean commitContent(SFMImageStack image) {
        SFMImageSnapshot snapshot = image.snapshot().orElseThrow(
                () -> new IllegalArgumentException("Cannot display an empty image stack")
        );
        return commitContent(snapshotLocation(snapshot), snapshot, image.interactionState());
    }

    private boolean commitContent(
            ResourceLocation imageRef,
            @Nullable SFMImageSnapshot snapshot,
            SFMValue state
    ) {
        if (level == null || level.isClientSide()) {
            throw new IllegalStateException("Touch Display content must be committed on the server");
        }
        Objects.requireNonNull(imageRef, "imageRef");
        Objects.requireNonNull(state, "state");

        DisplayContent previous = content;
        if (previous.imageRef().equals(imageRef)
            && Objects.equals(previous.imageSnapshot(), snapshot)
            && previous.state().equals(state)) {
            return false;
        }
        if (previous.revision() == Long.MAX_VALUE) {
            throw new IllegalStateException("Touch Display content revision exhausted");
        }

        // A state can fit its own value codec but overflow the touch event
        // after dimension, position, UV and revision are added. Commit neither
        // image nor state unless the complete event has sufficient headroom.
        SFMTouchValue.requireCommitEnvelopeFits(
                level.dimension().location(),
                worldPosition,
                getBlockState().getValue(TouchDisplayBlock.FACING),
                previous.revision() + 1,
                state
        );

        DisplayContent next = new DisplayContent(imageRef, snapshot, state, previous.revision() + 1);
        content = next;
        setChanged();
        BlockState blockState = getBlockState();
        level.sendBlockUpdated(worldPosition, blockState, blockState, Block.UPDATE_ALL);
        return true;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        content = readContent(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        CompoundTag saved = new CompoundTag();
        DisplayContent snapshot = content;
        saved.putString(IMAGE_TAG, snapshot.imageRef().toString());
        saved.putInt(VALUE_CODEC_TAG, SFMValueJsonCodec.VERSION);
        saved.putString(STATE_TAG, SFMValueJsonCodec.encode(snapshot.state()));
        saved.putLong(REVISION_TAG, snapshot.revision());
        if (snapshot.imageSnapshot() != null) {
            saved.put(SNAPSHOT_TAG, SFMImageSnapshotCodec.toTag(snapshot.imageSnapshot()));
        }
        tag.put(CONTENT_TAG, saved);
    }

    private static DisplayContent readContent(CompoundTag tag) {
        if (!tag.contains(CONTENT_TAG, Tag.TAG_COMPOUND)) {
            return fallbackContent(0);
        }
        CompoundTag saved = tag.getCompound(CONTENT_TAG);
        long savedRevision = saved.contains(REVISION_TAG, Tag.TAG_LONG)
                             ? Math.max(0, saved.getLong(REVISION_TAG))
                             : 0;
        if (!saved.contains(IMAGE_TAG, Tag.TAG_STRING)
            || !saved.contains(VALUE_CODEC_TAG, Tag.TAG_INT)
            || !saved.contains(STATE_TAG, Tag.TAG_STRING)
            || !saved.contains(REVISION_TAG, Tag.TAG_LONG)) {
            return fallbackContent(savedRevision);
        }
        try {
            ResourceLocation image = new ResourceLocation(saved.getString(IMAGE_TAG));
            SFMValue state = SFMValueJsonCodec.decode(
                    saved.getString(STATE_TAG), saved.getInt(VALUE_CODEC_TAG)
            );
            if (saved.contains(SNAPSHOT_TAG, Tag.TAG_COMPOUND)) {
                SFMImageSnapshot snapshot = SFMImageSnapshotCodec.fromTag(saved.getCompound(SNAPSHOT_TAG))
                        .orElseThrow(() -> new IllegalArgumentException("Invalid persisted display image"));
                return new DisplayContent(image, snapshot, state, saved.getLong(REVISION_TAG));
            }
            if (image.getNamespace().equals("sfm") && image.getPath().startsWith("image/")) {
                throw new IllegalArgumentException("Missing persisted display image payload");
            }
            return new DisplayContent(image, state, saved.getLong(REVISION_TAG));
        } catch (IllegalArgumentException | ResourceLocationException invalid) {
            // Fail closed on unknown content, but do not reuse a persisted revision.
            return fallbackContent(savedRevision);
        }
    }

    private static DisplayContent fallbackContent(long revision) {
        return new DisplayContent(DEFAULT_IMAGE, SFMValue.nullValue(), revision);
    }

    private static ResourceLocation snapshotLocation(SFMImageSnapshot snapshot) {
        return new ResourceLocation("sfm", "image/" + snapshot.sha256());
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable net.minecraft.core.Direction side) {
        if (cap == SFMWellKnownCapabilities.IMAGE_HANDLER.capabilityKind()) {
            return imageSinkCapability.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        imageSinkCapability.invalidate();
        super.invalidateCaps();
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        super.onDataPacket(connection, packet);
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            content = readContent(tag);
        }
    }
}
