package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.block.CableBlock;
import ca.teamdman.sfm.common.registry.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

public class CableBlockEntity extends BlockEntity {
    private @Nullable BlockState facadeState;

    public CableBlockEntity(BlockPos pos, BlockState state) {
        super(SFMBlockEntities.CABLE_BLOCK_ENTITY.get(), pos, state);
    }

    public @Nullable BlockState getFacadeState() {
        return facadeState;
    }

    public void setFacadeState(BlockState newState) {
        BlockState oldState = getBlockState();

        this.facadeState = newState;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, oldState, getBlockState(), Block.UPDATE_ALL);
        }
        requestModelDataUpdate();
    }

    @Override
    public ModelData getModelData() {
        return ModelData.builder().with(CableBlock.FACADE_BLOCK_STATE, facadeState).build();
    }

    @Override
    protected void loadAdditional(CompoundTag pTag, HolderLookup.Provider pRegistries) {
        super.loadAdditional(pTag, pRegistries);
        if (pTag.contains("facade")) {
            BlockState newState = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), pTag.getCompound("facade"));
            if (newState.getBlock() != SFMBlocks.CABLE_BLOCK.get())
                facadeState = newState;
            requestModelDataUpdate();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag pTag, HolderLookup.Provider pRegistries) {
        super.saveAdditional(pTag, pRegistries);
        if (facadeState != null)
            pTag.put("facade", NbtUtils.writeBlockState(facadeState));
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider lookupProvider) {
        super.onDataPacket(net, pkt, lookupProvider);

        loadAdditional(pkt.getTag(), lookupProvider);
        requestModelDataUpdate();
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider pRegistries) {
        CompoundTag pTag = new CompoundTag();
        saveAdditional(pTag, pRegistries);
        return pTag;
    }
}
