package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.block.BufferBlock;
import ca.teamdman.sfm.common.block.BufferBlockTier;
import ca.teamdman.sfm.common.capability.BufferBlockCapabilityProvider;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityResult;
import ca.teamdman.sfm.common.capability.IImageHandler;
import ca.teamdman.sfm.common.image.SFMImageSnapshotCodec;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.SFMImageStack;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueJsonCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Optional;

public class BufferBlockEntity extends BlockEntity {
    private static final String IMAGE_TAG = "image_snapshot";
    private static final String IMAGE_STATE_TAG = "image_interaction_state";
    private static final String IMAGE_STATE_CODEC_TAG = "image_state_codec";
    private final BufferBlockEntityContents contents;
    private final ArrayList<LazyOptional<?>> toInvalidate = new ArrayList<>();

    public BufferBlockEntity(
            BlockPos pPos,
            BlockState pBlockState
    ) {
        super(SFMBlockEntities.BUFFER.get(), pPos, pBlockState);
        BufferBlockTier tier = pBlockState.getBlock() instanceof BufferBlock bufferBlock
                               ? bufferBlock.tier
                               : BufferBlockTier.Unit;
        this.contents = new BufferBlockEntityContents(tier, this::setChanged);
    }

    /** Only image resources are durable in this first buffer persistence slice. */
    @MCVersionDependentBehaviour
    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // Restore in place: cached capability handles must observe replacement
        // and clearing, including absent or malformed image NBT. Capability
        // exclusion leaves an occupied nonpersisted resource untouched.
        imageHandler().ifPresent(handler -> handler.extractImage(false));
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

    @MCVersionDependentBehaviour
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        imageHandler().ifPresent(handler -> {
            SFMImageStack image = handler.getImage();
            image.snapshot().ifPresent(snapshot -> {
                tag.put(IMAGE_TAG, SFMImageSnapshotCodec.toTag(snapshot));
                tag.putString(IMAGE_STATE_TAG, SFMValueJsonCodec.encode(image.interactionState()));
                tag.putInt(IMAGE_STATE_CODEC_TAG, SFMValueJsonCodec.VERSION);
            });
        });
    }

    private Optional<IImageHandler> imageHandler() {
        SFMBlockCapabilityResult<IImageHandler> capability = contents.getCapability(SFMResourceTypes.IMAGE.get());
        return capability.isPresent() ? Optional.of(capability.unwrap()) : Optional.empty();
    }

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
