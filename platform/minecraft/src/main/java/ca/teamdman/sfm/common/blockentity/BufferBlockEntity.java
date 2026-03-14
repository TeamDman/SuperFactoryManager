package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.block.BufferBlock;
import ca.teamdman.sfm.common.block.BufferBlockTier;
import ca.teamdman.sfm.common.capability.BufferBlockCapabilityProvider;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityResult;
import ca.teamdman.sfm.common.registry.SFMBlockEntities;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class BufferBlockEntity extends BlockEntity {
    private final BufferBlockEntityContents contents;
    private final ArrayList<LazyOptional<?>> toInvalidate = new ArrayList<>();

    public BufferBlockEntity(
            BlockPos pPos,
            BlockState pBlockState
    ) {
        super(SFMBlockEntities.BUFFER_BLOCK_ENTITY.get(), pPos, pBlockState);
        BufferBlockTier tier = pBlockState.getBlock() instanceof BufferBlock bufferBlock
                               ? bufferBlock.tier
                               : BufferBlockTier.Unit;
        this.contents = new BufferBlockEntityContents(tier);
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
