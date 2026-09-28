package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.facade.FacadeData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

public abstract class CommonFacadeBlockEntity extends BlockEntity implements IFacadeBlockEntity {
    protected @Nullable FacadeData facadeData = null;

    public CommonFacadeBlockEntity(
            BlockEntityType<?> pType,
            BlockPos pPos,
            BlockState pBlockState
    ) {
        super(pType, pPos, pBlockState);
    }

    @Override
    public @Nullable FacadeData getFacadeData() {
        return facadeData;
    }

    @Override
    public void updateFacadeData(
            FacadeData newFacadeData
    ) {
        if (newFacadeData.equals(facadeData)) return;
        this.facadeData = newFacadeData;
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_IMMEDIATE);
        }
        requestModelDataUpdate();
    }

    @Override
    public abstract ModelData getModelData();

    @Override
    protected void loadAdditional(
            ValueInput input
    ) {
        super.loadAdditional(input);
        FacadeData tried = FacadeData.load(level, input);
        if (tried != null) {
            this.facadeData = tried;
            requestModelDataUpdate();
        }
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(
            ValueOutput output
    ) {
        super.saveAdditional(output);
        if (facadeData != null) {
            facadeData.save(output);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
            return saveWithoutMetadata(registries);
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        super.handleUpdateTag(input);
    }
}
