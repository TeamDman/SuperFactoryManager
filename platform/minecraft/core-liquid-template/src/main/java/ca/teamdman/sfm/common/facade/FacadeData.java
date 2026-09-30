package ca.teamdman.sfm.common.facade;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% when '26.1.2' %}
{% endcase %}
import net.minecraft.core.Direction;
{% case minecraft_version %}
{% when '1.19.2' %}
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
{% when '26.1.2' %}
{% endcase %}
import net.minecraft.world.level.Level;
{% case minecraft_version %}
{% when '1.19.2', '26.1.2' %}
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.world.level.block.Block;
{% endcase %}
import net.minecraft.world.level.block.state.BlockState;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
{% endcase %}
import org.jetbrains.annotations.Nullable;

public record FacadeData(
        BlockState facadeBlockState,
        Direction facadeDirection,
        FacadeTextureMode facadeTextureMode
) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public void save(CompoundTag tag) {
        CompoundTag facadeTag = new CompoundTag();
        facadeTag.put("block_state", NbtUtils.writeBlockState(this.facadeBlockState()));
        facadeTag.putString("direction", this.facadeDirection().getSerializedName());
        facadeTag.putString("texture_mode", this.facadeTextureMode().getSerializedName());
        tag.put("sfm:facade", facadeTag);
{% when '26.1.2' %}
    public void save(ValueOutput output) {
        ValueOutput facadeOutput = output.child("sfm:facade");
        facadeOutput.store("block_state", BlockState.CODEC, this.facadeBlockState());
        facadeOutput.store("direction", Direction.CODEC, this.facadeDirection());
        facadeOutput.store("texture_mode", FacadeTextureMode.CODEC, this.facadeTextureMode());
{% endcase %}
    }

    public static @Nullable FacadeData load(
{% case minecraft_version %}
{% when '1.19.2' %}
            @SuppressWarnings("unused") @Nullable Level level,
            CompoundTag tag
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            @Nullable Level level,
            CompoundTag tag
{% when '26.1.2' %}
            @Nullable Level level,
            ValueInput input
{% endcase %}
    ) {
{% case minecraft_version %}
{% when '1.19.2' %}
        if (tag.contains("sfm:facade", CompoundTag.TAG_COMPOUND)) {
            CompoundTag facadeTag = tag.getCompound("sfm:facade");
            BlockState facadeState = readBlockState(facadeTag.getCompound("block_state"));
            Direction facadeDirection = Direction.byName(facadeTag.getString("direction"));
            FacadeTextureMode facadeTextureMode = FacadeTextureMode.byName(facadeTag.getString("texture_mode"));
            if (facadeTextureMode != null && facadeDirection != null) {
                return new FacadeData(facadeState, facadeDirection, facadeTextureMode);
            }
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        if (tag.contains("sfm:facade", CompoundTag.TAG_COMPOUND)) {
            CompoundTag facadeTag = tag.getCompound("sfm:facade");
            BlockState facadeState = readBlockState(facadeTag.getCompound("block_state"), level);
            Direction facadeDirection = Direction.byName(facadeTag.getString("direction"));
            FacadeTextureMode facadeTextureMode = FacadeTextureMode.byName(facadeTag.getString("texture_mode"));
            if (facadeTextureMode != null && facadeDirection != null) {
                return new FacadeData(facadeState, facadeDirection, facadeTextureMode);
            }
{% when '26.1.2' %}
        if (input.child("sfm:facade").isPresent()) {
            ValueInput facadeTag = input.child("sfm:facade").get();
            BlockState facadeState = facadeTag.read("block_state", BlockState.CODEC).get();
            Direction facadeDirection = facadeTag.read("direction", Direction.CODEC).get();
            FacadeTextureMode facadeTextureMode = facadeTag.read("texture_mode", FacadeTextureMode.CODEC).get();

            return new FacadeData(facadeState, facadeDirection, facadeTextureMode);
{% endcase %}
        }
        return null;
    }

    /**
     * See {@link net.minecraft.world.level.block.piston.MovingPistonBlock::load}
     */
{% case minecraft_version %}
{% when '1.19.2' %}
    @MCVersionDependentBehaviour
    private static BlockState readBlockState(CompoundTag tag) {
        return NbtUtils.readBlockState(tag);
    }
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    @MCVersionDependentBehaviour
    private static BlockState readBlockState(
            CompoundTag tag,
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
{% when '26.1.2' %}
/*    @MCVersionDependentBehaviour
    private static BlockState readBlockState(
            CompoundTag tag,
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
    }*/
{% endcase %}
}
