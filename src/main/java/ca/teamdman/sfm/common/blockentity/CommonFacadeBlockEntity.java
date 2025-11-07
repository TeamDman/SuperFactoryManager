package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.facade.FacadeData;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

public abstract class CommonFacadeBlockEntity extends TileEntity implements IFacadeBlockEntity {
    protected @Nullable FacadeData facadeData = null;

    public CommonFacadeBlockEntity(
            BlockPos pPos,
            BlockState pBlockState
    ) {
        super(pPos, pBlockState);
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
    public void load(NBTTagCompound pTag) {
        super.load(pTag);
        FacadeData tried = FacadeData.load(level, pTag);
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
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound pTag = new NBTTagCompound();
        saveAdditional(pTag);
        return pTag;
    }

    @Override
    protected void saveAdditional(NBTTagCompound pTag) {
        super.saveAdditional(pTag);
        if (facadeData != null) {
            facadeData.save(pTag);
        }
    }
}
