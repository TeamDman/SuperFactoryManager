package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueJsonCodec;
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
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Server-owned display content, replicated as one immutable snapshot. */
public class TouchDisplayBlockEntity extends BlockEntity {
    private static final String CONTENT_TAG = "display_content";
    private static final String IMAGE_TAG = "image";
    private static final String STATE_TAG = "state";
    private static final String VALUE_CODEC_TAG = "value_codec";
    private static final String REVISION_TAG = "revision";

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
    public record DisplayContent(ResourceLocation imageRef, SFMValue state, long revision) {
        public DisplayContent {
            Objects.requireNonNull(imageRef, "imageRef");
            Objects.requireNonNull(state, "state");
            if (revision < 0) {
                throw new IllegalArgumentException("Display content revision must be non-negative");
            }
            SFMValueJsonCodec.encode(state);
        }
    }

    private volatile DisplayContent content = new DisplayContent(DEFAULT_IMAGE, SFMValue.nullValue(), 0);

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
        if (level == null || level.isClientSide()) {
            throw new IllegalStateException("Touch Display content must be committed on the server");
        }
        Objects.requireNonNull(imageRef, "imageRef");
        Objects.requireNonNull(state, "state");

        DisplayContent previous = content;
        if (previous.imageRef().equals(imageRef) && previous.state().equals(state)) {
            return false;
        }
        if (previous.revision() == Long.MAX_VALUE) {
            throw new IllegalStateException("Touch Display content revision exhausted");
        }

        DisplayContent next = new DisplayContent(imageRef, state, previous.revision() + 1);
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
            return new DisplayContent(image, state, saved.getLong(REVISION_TAG));
        } catch (IllegalArgumentException | ResourceLocationException invalid) {
            // Fail closed on unknown content, but do not reuse a persisted revision.
            return fallbackContent(savedRevision);
        }
    }

    private static DisplayContent fallbackContent(long revision) {
        return new DisplayContent(DEFAULT_IMAGE, SFMValue.nullValue(), revision);
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
