package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.facade.FacadeData;
import net.minecraft.core.BlockPos;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.core.HolderLookup;
{% endcase %}
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.client.model.data.ModelData;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.neoforged.neoforge.client.model.data.ModelData;
{% when '26.1.2' %}
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.model.data.ModelData;
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
    public void load(CompoundTag pTag) {
        super.load(pTag);
        FacadeData tried = FacadeData.load(level, pTag);
{% when '1.21', '1.21.1' %}
    protected void loadAdditional(
            CompoundTag pTag,
            HolderLookup.Provider pRegistries
    ) {
        super.loadAdditional(pTag, pRegistries);
        FacadeData tried = FacadeData.load(level, pTag);
{% when '26.1.2' %}
    protected void loadAdditional(
            ValueInput input
    ) {
        super.loadAdditional(input);
        FacadeData tried = FacadeData.load(level, input);
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
    public CompoundTag getUpdateTag() {
        CompoundTag pTag = new CompoundTag();
        saveAdditional(pTag);
        return pTag;
{% when '1.21', '1.21.1' %}
    public CompoundTag getUpdateTag(HolderLookup.Provider pRegistries) {
        CompoundTag pTag = new CompoundTag();
        saveAdditional(pTag, pRegistries);
        return pTag;
{% when '26.1.2' %}
    protected void saveAdditional(
            ValueOutput output
    ) {
        super.saveAdditional(output);
        if (facadeData != null) {
            facadeData.save(output);
        }
{% endcase %}
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
    protected void saveAdditional(CompoundTag pTag) {
        super.saveAdditional(pTag);
        if (facadeData != null) {
            facadeData.save(pTag);
        }
{% when '1.21', '1.21.1' %}
    protected void saveAdditional(
            CompoundTag pTag,
            HolderLookup.Provider pRegistries
    ) {
        super.saveAdditional(pTag, pRegistries);
        if (facadeData != null) {
            facadeData.save(pTag);
        }
{% when '26.1.2' %}
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
            return saveWithoutMetadata(registries);
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        super.handleUpdateTag(input);
{% endcase %}
    }
}
