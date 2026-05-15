package ca.teamdman.sfm.common.facade;

import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

public record FacadeData(
        BlockState facadeBlockState,
        Direction facadeDirection,
        FacadeTextureMode facadeTextureMode
) {
    public void save(ValueOutput output) {
        ValueOutput facadeOutput = output.child("sfm:facade");
        facadeOutput.store("block_state", BlockState.CODEC, this.facadeBlockState());
        facadeOutput.store("direction", Direction.CODEC, this.facadeDirection());
        facadeOutput.store("texture_mode", FacadeTextureMode.CODEC, this.facadeTextureMode());
    }

    public static @Nullable FacadeData load(
            @Nullable Level level,
            ValueInput input
    ) {
        if (input.child("sfm:facade").isPresent()) {
            ValueInput facadeTag = input.child("sfm:facade").get();
            BlockState facadeState = facadeTag.read("block_state", BlockState.CODEC).get();
            Direction facadeDirection = facadeTag.read("direction", Direction.CODEC).get();
            FacadeTextureMode facadeTextureMode = facadeTag.read("texture_mode", FacadeTextureMode.CODEC).get();

            return new FacadeData(facadeState, facadeDirection, facadeTextureMode);
        }
        return null;
    }

    /**
     * See {@link net.minecraft.world.level.block.piston.MovingPistonBlock::load}
     */
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
}
