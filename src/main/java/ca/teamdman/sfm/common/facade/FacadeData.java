package ca.teamdman.sfm.common.facade;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.util.EnumFacing;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.Level;
import net.minecraft.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public record FacadeData(
        BlockState facadeBlockState,
        EnumFacing facadeDirection,
        FacadeTextureMode facadeTextureMode
) {
    public void save(NBTTagCompound tag) {
        NBTTagCompound facadeTag = new NBTTagCompound();
        facadeTag.put("block_state", NbtUtils.writeBlockState(this.facadeBlockState()));
        facadeTag.putString("direction", this.facadeDirection().getSerializedName());
        facadeTag.putString("texture_mode", this.facadeTextureMode().getSerializedName());
        tag.put("sfm:facade", facadeTag);
    }

    public static @Nullable FacadeData load(
            @Nullable Level level,
            NBTTagCompound tag
    ) {
        if (tag.contains("sfm:facade", NBTTagCompound.TAG_COMPOUND)) {
            NBTTagCompound facadeTag = tag.getCompound("sfm:facade");
            BlockState facadeState = readBlockState(facadeTag.getCompound("block_state"), level);
            EnumFacing facadeDirection = EnumFacing.byName(facadeTag.getString("direction"));
            FacadeTextureMode facadeTextureMode = FacadeTextureMode.byName(facadeTag.getString("texture_mode"));
            if (facadeTextureMode != null && facadeDirection != null) {
                return new FacadeData(facadeState, facadeDirection, facadeTextureMode);
            }
        }
        return null;
    }

    /**
     * See {@link net.minecraft.world.level.block.piston.MovingPistonBlock::load}
     */
    @MCVersionDependentBehaviour
    private static BlockState readBlockState(
            NBTTagCompound tag,
            @Nullable Level level
    ) {
        @SuppressWarnings("deprecation")
        HolderGetter<Block> holderGetter = level != null
                                           ? level.holderLookup(Registries.BLOCK)
                                           : BuiltInRegistries.BLOCK.asLookup();
        return NbtUtils.readBlockState(
                holderGetter,
                tag
        );
    }
}
