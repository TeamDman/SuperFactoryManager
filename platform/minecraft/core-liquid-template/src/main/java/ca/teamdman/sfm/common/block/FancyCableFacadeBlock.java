package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.facade.FacadeTransparency;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% endcase %}
import net.minecraft.core.BlockPos;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.world.entity.player.Player;
{% endcase %}
import net.minecraft.world.item.ItemStack;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
import net.minecraft.world.level.BlockGetter;
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.world.level.LevelReader;
{% endcase %}
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jetbrains.annotations.Nullable;

public class FancyCableFacadeBlock extends FancyCableBlock implements EntityBlock, IFacadableBlock {
    public FancyCableFacadeBlock(Properties properties) {
        super(properties.lightLevel(LightBlock.LIGHT_EMISSION));
        registerDefaultState(
                defaultBlockState()
                        .setValue(FacadeTransparency.FACADE_TRANSPARENCY_PROPERTY, FacadeTransparency.TRANSLUCENT)
                        .setValue(LightBlock.LEVEL, 0)
        );
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(
            BlockPos blockPos,
            BlockState blockState
    ) {
        return SFMBlockEntities.FANCY_CABLE_FACADE.get().create(blockPos, blockState);
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    @SuppressWarnings("deprecation")
{% when '26.1.2' %}
{% endcase %}
    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
    public ItemStack getCloneItemStack(
            BlockGetter pLevel,
            BlockPos pPos,
            BlockState pState
    ) {
{% when '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public ItemStack getCloneItemStack(
            @MCVersionDependentBehaviour LevelReader pLevel,
            BlockPos pPos,
            BlockState pState
    ) {
{% when '26.1.2' %}
    public ItemStack getCloneItemStack(@MCVersionDependentBehaviour LevelReader level, BlockPos pos, BlockState state, boolean includeData, Player player) {
{% endcase %}
        return new ItemStack(SFMBlocks.FANCY_CABLE.get());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        createFacadeBlockStateDefinition(builder);
    }
}
