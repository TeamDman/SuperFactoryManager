package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.block.BufferBlock;
import ca.teamdman.sfm.common.block.BufferBlockTier;
{% case minecraft_version %}
{% when "1.20.3", "1.20.4", "1.21", "1.21.0", "1.21.1", "26.1.2" %}
{% else %}
import ca.teamdman.sfm.common.capability.BufferBlockCapabilityProvider;
{% if features.buffer_image_persistence %}
import ca.teamdman.sfm.common.capability.IImageHandler;
{% endif %}
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityResult;
{% if features.buffer_image_persistence %}
import ca.teamdman.sfm.common.image.SFMImageSnapshotCodec;
{% endif %}
{% endcase %}
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
{% case minecraft_version %}
{% when "1.20.3", "1.20.4", "1.21", "1.21.0", "1.21.1", "26.1.2" %}
{% else %}
{% if features.buffer_image_persistence %}
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
{% endif %}
{% if features.buffer_image_persistence %}
import ca.teamdman.sfm.common.resourcetype.SFMImageStack;
{% endif %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% if features.buffer_image_persistence %}
import ca.teamdman.sfm.common.value.SFMValue;
{% endif %}
{% if features.buffer_image_persistence %}
import ca.teamdman.sfm.common.value.SFMValueJsonCodec;
{% endif %}
{% endcase %}
import net.minecraft.core.BlockPos;
{% case minecraft_version %}
{% when "1.20.3", "1.20.4", "1.21", "1.21.0", "1.21.1", "26.1.2" %}
{% else %}
import net.minecraft.core.Direction;
{% if features.redstone_buffer_storage or features.buffer_image_persistence %}
import net.minecraft.nbt.CompoundTag;
{% endif %}
{% if features.buffer_image_persistence %}
import net.minecraft.nbt.Tag;
{% endif %}
{% endcase %}
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
{% case minecraft_version %}
{% when "1.20.3", "1.20.4", "1.21", "1.21.0", "1.21.1", "26.1.2" %}
{% else %}
{% case minecraft_version %}
{% when "1.20.2" %}
import net.neoforged.neoforge.common.capabilities.Capability;
import net.neoforged.neoforge.common.util.LazyOptional;
{% else %}
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
{% endcase %}
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
{% endcase %}

{% case minecraft_version %}
{% when "1.20.3", "1.20.4", "1.21", "1.21.0", "1.21.1", "26.1.2" %}
{% else %}
import java.util.ArrayList;
{% if features.buffer_image_persistence %}
import java.util.Optional;
{% endif %}

{% endcase %}
public class BufferBlockEntity extends BlockEntity {
{% case minecraft_version %}
{% when "1.20.3", "1.20.4", "1.21", "1.21.0", "1.21.1", "26.1.2" %}
{% else %}
{% if features.buffer_image_persistence %}
    private static final String IMAGE_TAG = "image_snapshot";
    private static final String IMAGE_STATE_TAG = "image_interaction_state";
    private static final String IMAGE_STATE_CODEC_TAG = "image_state_codec";
{% endif %}
{% endcase %}
    private final BufferBlockEntityContents contents;
{% case minecraft_version %}
{% when "1.20.3", "1.20.4", "1.21", "1.21.0", "1.21.1", "26.1.2" %}
{% else %}
    private final ArrayList<LazyOptional<?>> toInvalidate = new ArrayList<>();
{% endcase %}

    public BufferBlockEntity(
            BlockPos pPos,
            BlockState pBlockState
    ) {
        super(SFMBlockEntities.BUFFER.get(), pPos, pBlockState);
        BufferBlockTier tier = pBlockState.getBlock() instanceof BufferBlock bufferBlock
                               ? bufferBlock.tier
                               : BufferBlockTier.Unit;
{% case minecraft_version %}
{% when "1.20.3", "1.20.4", "1.21", "1.21.0", "1.21.1", "26.1.2" %}
        this.contents = new BufferBlockEntityContents(tier);
{% else %}
{% if features.redstone_buffer_storage or features.image_resources %}
        this.contents = new BufferBlockEntityContents(tier, this::setChanged);
{% else %}
        this.contents = new BufferBlockEntityContents(tier);
{% endif %}
    }

{% if features.redstone_buffer_storage or features.buffer_image_persistence %}
    /** Redstone and complete image/state resources are durable; other resources remain transient. */
    @MCVersionDependentBehaviour
    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
{% if features.buffer_image_persistence %}
        // Clear the old image before restoring redstone so persisted resources
        // can replace each other without replacing cached capability handlers.
        // Occupied nonpersisted handlers remain protected by resource exclusion.
        imageHandler().ifPresent(handler -> handler.extractImage(false));
{% endif %}
{% if features.redstone_buffer_storage %}
        contents.loadRedstone(tag.getLong("redstone"));
{% endif %}
{% if features.buffer_image_persistence %}
        // Redstone wins if malformed NBT supplies both persisted resource types.
        loadImage(tag);
{% endif %}
        // Also refresh comparators when NBT is applied to an existing block.
        setChanged();
    }

{% if features.buffer_image_persistence %}
    private void loadImage(CompoundTag tag) {
        if (!tag.contains(IMAGE_TAG, Tag.TAG_COMPOUND)
            || !tag.contains(IMAGE_STATE_TAG, Tag.TAG_STRING)
            || !tag.contains(IMAGE_STATE_CODEC_TAG, Tag.TAG_INT)) {
            return;
        }
        SFMImageSnapshotCodec.fromTag(tag.getCompound(IMAGE_TAG)).ifPresent(snapshot -> {
            try {
                SFMValue state = SFMValueJsonCodec.decode(
                        tag.getString(IMAGE_STATE_TAG), tag.getInt(IMAGE_STATE_CODEC_TAG)
                );
                imageHandler().ifPresent(handler ->
                        handler.insertImage(SFMImageStack.of(snapshot, state), false)
                );
            } catch (IllegalArgumentException ignored) {
                // Malformed persisted resources are not loaded or partially inserted.
            }
        });
    }

{% endif %}
    @MCVersionDependentBehaviour
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
{% if features.redstone_buffer_storage %}
        tag.putInt("redstone", contents.getStoredRedstone());
{% endif %}
{% if features.buffer_image_persistence %}
        imageHandler().ifPresent(handler -> {
            SFMImageStack image = handler.getImage();
            image.snapshot().ifPresent(snapshot -> {
                tag.put(IMAGE_TAG, SFMImageSnapshotCodec.toTag(snapshot));
                tag.putString(IMAGE_STATE_TAG, SFMValueJsonCodec.encode(image.interactionState()));
                tag.putInt(IMAGE_STATE_CODEC_TAG, SFMValueJsonCodec.VERSION);
            });
        });
{% endif %}
    }

{% if features.buffer_image_persistence %}
    private Optional<IImageHandler> imageHandler() {
        SFMBlockCapabilityResult<IImageHandler> capability = contents.getCapability(SFMResourceTypes.IMAGE.get());
        return capability.isPresent() ? Optional.of(capability.unwrap()) : Optional.empty();
    }

{% endif %}
{% endif %}
    @Override
    public void invalidateCaps() {
        for (LazyOptional<?> cap : toInvalidate) {
            cap.invalidate();
        }
        toInvalidate.clear();
        super.invalidateCaps();
    }

    @SuppressWarnings("unchecked")
    @MCVersionDependentBehaviour
    @Override
    public @NotNull <T> LazyOptional<T> getCapability(
            @NotNull Capability<T> cap,
            @Nullable Direction side
    ) {
        SFMBlockCapabilityKind<T> capKind = new SFMBlockCapabilityKind<>(cap);
        BufferBlockCapabilityProvider bufferBlockCapabilityProvider = new BufferBlockCapabilityProvider();
        assert level != null;
        SFMBlockCapabilityResult<T> found = (SFMBlockCapabilityResult<T>) bufferBlockCapabilityProvider.getCapability(
                (SFMBlockCapabilityKind<Object>) capKind,
                level,
                getBlockPos(),
                getBlockState(),
                this,
                side
        );
        if (found.isPresent()) {
            // create a copy so that we can invalidate it without affecting the original
            LazyOptional<T> rtn = found.inner().lazyMap(x->x);
            toInvalidate.add(rtn);
            return rtn;
        } else {
            return LazyOptional.empty();
        }
{% endcase %}
    }

    public BufferBlockEntityContents getContents() {
        return contents;
    }


    public static void serverTick(
            @SuppressWarnings("unused") Level level,
            @SuppressWarnings("unused") BlockPos pos,
            @SuppressWarnings("unused") BlockState state,
            BufferBlockEntity bufferBlockEntity
    ) {
        if (bufferBlockEntity.getContents().lastUsedResource != state.getValue(BufferBlock.CONTAINED_RESOURCE)) {
            level.setBlock(pos,
                           state.setValue(
                                   BufferBlock.CONTAINED_RESOURCE,
                                   bufferBlockEntity.getContents().lastUsedResource
                           ),
                           Block.UPDATE_CLIENTS
            );
        }
    }
}
