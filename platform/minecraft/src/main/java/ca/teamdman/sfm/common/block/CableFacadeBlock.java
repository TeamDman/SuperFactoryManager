package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.facade.FacadeData;
import ca.teamdman.sfm.common.facade.FacadeProperties;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.extensions.IBlockStateExtension;
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
                        .setValue(FacadeProperties.SOLID, true)
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
        return pState.getValue(FacadeProperties.SOLID) ?
                Shapes.block() :
                Shapes.empty();
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData, Player player) {
        return new ItemStack(SFMBlocks.CABLE.get());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        createFacadeBlockStateDefinition(builder);
        builder.add(FacadeProperties.SOLID);
    }

    @Override
    public BlockState getAppearance(BlockState state, BlockAndLightGetter level, BlockPos pos, Direction side, @Nullable BlockState queryState, @Nullable BlockPos queryPos) {
        BlockState mimicState = FacadeData.resolveAppearance(level, pos, state);
        if (state == mimicState) {
            return super.getAppearance(state, level, pos, side, queryState, queryPos);
        }
        return mimicState.getAppearance(level, pos, side, queryState, queryPos);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return !state.getValue(FacadeProperties.SOLID);
    }

    @Override
    public boolean supportsExternalFaceHiding(BlockState state) {
        return !state.getValue(FacadeProperties.SOLID);
    }

    @Override
    public boolean hidesNeighborFace(
            BlockGetter level,
            BlockPos pos,
            BlockState state,
            BlockState neighborState,
            Direction dir
    ) {
        BlockState ourAppearance = FacadeData.resolveAppearance(level, pos, state);
        BlockPos neighborPos = pos.relative(dir);
        BlockState neighborAppearance = FacadeData.resolveAppearance(level, neighborPos, neighborState);
        return ourAppearance.skipRendering(neighborAppearance, dir);
    }

/*    @Override
    protected boolean skipRendering(BlockState state, BlockState neighborState, Direction direction) {
        return false;
    }*/
}
