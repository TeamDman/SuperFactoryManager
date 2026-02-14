package ca.teamdman.sfm.common.facade;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import org.jetbrains.annotations.Nullable;

@Desugar
public record FacadeData(
        IBlockState facadeBlockState,
        EnumFacing facadeDirection,
        FacadeTextureMode facadeTextureMode
) {
    public static final PropertyInteger LIGHT_LEVEL = PropertyInteger.create("level", 0, 15);

    public void save(NBTTagCompound tag) {
        NBTTagCompound facadeTag = new NBTTagCompound();
        facadeTag.setTag("block_state", NBTUtil.writeBlockState(new NBTTagCompound(), this.facadeBlockState()));
        facadeTag.setString("direction", this.facadeDirection().getName2());
        facadeTag.setString("texture_mode", this.facadeTextureMode().getSerializedName());
        tag.setTag("sfm:facade", facadeTag);
    }

    public static @Nullable FacadeData load(
            @SuppressWarnings("unused") @Nullable World level,
            NBTTagCompound tag
    ) {
        if (tag.hasKey("sfm:facade", Constants.NBT.TAG_COMPOUND)) {
            NBTTagCompound facadeTag = tag.getCompoundTag("sfm:facade");
            IBlockState facadeState = readBlockState(facadeTag.getCompoundTag("block_state"));
            EnumFacing facadeDirection = EnumFacing.byName(facadeTag.getString("direction"));
            FacadeTextureMode facadeTextureMode = FacadeTextureMode.byName(facadeTag.getString("texture_mode"));
            if (facadeTextureMode != null && facadeDirection != null) {
                return new FacadeData(facadeState, facadeDirection, facadeTextureMode);
            }
        }
        return null;
    }

    @MCVersionDependentBehaviour
    private static IBlockState readBlockState(NBTTagCompound tag) {
        return NBTUtil.readBlockState(tag);
    }
}
