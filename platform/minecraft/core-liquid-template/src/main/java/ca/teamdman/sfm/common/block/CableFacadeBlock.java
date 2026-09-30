package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.facade.FacadeTransparency;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
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
{% when '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
{% when '26.1.2' %}
import net.minecraft.world.level.LevelReader;
{% endcase %}
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;


public class CableFacadeBlock extends CableBlock implements EntityBlock, IFacadableBlock {
    @SFMLocalizationDatagen
    public static final LocalizationEntry CABLE_FACADE_BLOCK = new LocalizationEntry(
            () -> SFMBlocks.CABLE_FACADE.get().getDescriptionId(),
            () -> "Inventory Cable Facade"
    );

    public CableFacadeBlock(Properties properties) {

        super(properties.lightLevel(LightBlock.LIGHT_EMISSION));
        registerDefaultState(
                getStateDefinition()
                        .any()
                        .setValue(
                                FacadeTransparency.FACADE_TRANSPARENCY_PROPERTY,
                                FacadeTransparency.OPAQUE
                        )
                        .setValue(LightBlock.LEVEL, 0)
        );
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(
            BlockPos blockPos,
            BlockState blockState
    ) {

        return SFMBlockEntities.CABLE_FACADE.get().create(blockPos, blockState);
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    @SuppressWarnings("deprecation")
{% when '26.1.2' %}
{% endcase %}
    @Override
    public VoxelShape getOcclusionShape(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            BlockState pState,
            BlockGetter pLevel,
            BlockPos pPos
{% when '26.1.2' %}
            BlockState pState
{% endcase %}
    ) {
        // Translucent blocks should have no occlusion
        return pState.getValue(FacadeTransparency.FACADE_TRANSPARENCY_PROPERTY) == FacadeTransparency.TRANSLUCENT ?
               Shapes.empty() :
               Shapes.block();
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
            LevelReader pLevel,
            BlockPos pPos,
            BlockState pState
    ) {

{% when '26.1.2' %}
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData, Player player) {
{% endcase %}
        return new ItemStack(SFMBlocks.CABLE.get());
    }

    @Override
    public boolean propagatesSkylightDown(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            BlockState pState,
            BlockGetter pLevel,
            BlockPos pPos
{% when '26.1.2' %}
            BlockState pState
{% endcase %}
    ) {

        return pState.getValue(FacadeTransparency.FACADE_TRANSPARENCY_PROPERTY) == FacadeTransparency.TRANSLUCENT;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {

        createFacadeBlockStateDefinition(builder);
    }

}
