package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.facade.FacadeTransparency;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
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

    @Override
    public VoxelShape getOcclusionShape(
            BlockState pState
    ) {
        // Translucent blocks should have no occlusion
        return pState.getValue(FacadeTransparency.FACADE_TRANSPARENCY_PROPERTY) == FacadeTransparency.TRANSLUCENT ?
               Shapes.empty() :
               Shapes.block();
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData, Player player) {
        return new ItemStack(SFMBlocks.CABLE.get());
    }

    @Override
    public boolean propagatesSkylightDown(
            BlockState pState
    ) {

        return pState.getValue(FacadeTransparency.FACADE_TRANSPARENCY_PROPERTY) == FacadeTransparency.TRANSLUCENT;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {

        createFacadeBlockStateDefinition(builder);
    }

}
