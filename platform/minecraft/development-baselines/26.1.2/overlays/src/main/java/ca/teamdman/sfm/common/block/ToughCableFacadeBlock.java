package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.blockentity.IFacadeBlockEntity;
import ca.teamdman.sfm.common.facade.FacadeData;
import ca.teamdman.sfm.common.facade.FacadeTransparency;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class ToughCableFacadeBlock extends CableFacadeBlock implements EntityBlock, IFacadableBlock {
    @SFMLocalizationDatagen
    public static final LocalizationEntry TOUGH_CABLE_FACADE_BLOCK = new LocalizationEntry(
            () -> SFMBlocks.TOUGH_CABLE_FACADE.get().getDescriptionId(),
            () -> "Tough Inventory Cable Facade"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry FANCY_CABLE_FACADE_BLOCK = new LocalizationEntry(
            () -> SFMBlocks.FANCY_CABLE_FACADE.get().getDescriptionId(),
            () -> "Fancy Inventory Cable Facade"
    );

    public ToughCableFacadeBlock(Properties properties) {

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

        return SFMBlockEntities.TOUGH_CABLE_FACADE.get().create(blockPos, blockState);
    }

    @Override
    public ItemStack getCloneItemStack(@MCVersionDependentBehaviour LevelReader level, BlockPos pos, BlockState state, boolean includeData, Player player) {
        return new ItemStack(SFMBlocks.TOUGH_CABLE.get());
    }

    @Override
    public IFacadableBlock getNonFacadeBlock() {

        return SFMBlocks.TOUGH_CABLE.get();
    }

    @Override
    public IFacadableBlock getFacadeBlock() {

        return SFMBlocks.TOUGH_CABLE_FACADE.get();
    }

    @Override
    public boolean canEntityDestroy(
            BlockState state,
            BlockGetter level,
            BlockPos blockPos,
            Entity entity
    ) {

        return canEntityDestroyFacaded(state, level, blockPos, entity);
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getExplosionResistance(
            BlockState state,
            BlockGetter world,
            BlockPos pos,
            Explosion explosion
    ) {

        return getFacadedToughCableExplosionResistance(world, pos, super.getExplosionResistance());
    }

    static boolean canEntityDestroyFacaded(
            BlockState ignoredState,
            BlockGetter level,
            BlockPos blockPos,
            Entity entity
    ) {

        BlockEntity blockEntity = level.getBlockEntity(blockPos);

        // Ensure it has a facade
        if (!(blockEntity instanceof IFacadeBlockEntity facadeBlockEntity)) {
            return true;
        }
        FacadeData facadeData = facadeBlockEntity.getFacadeData();
        if (facadeData == null) {
            return true;
        }

        // delegate to the mimicked block to check if the destruction should succeed
        BlockState mimickingBlockState = facadeData.facadeBlockState();
        return mimickingBlockState.canEntityDestroy(level, blockPos, entity);
    }

    @SuppressWarnings("deprecation")
    static float getFacadedToughCableExplosionResistance(
            BlockGetter world,
            BlockPos pos,
            float defaultResistence
    ) {

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof IFacadeBlockEntity facadeBlockEntity) {
            FacadeData fd = facadeBlockEntity.getFacadeData();
            if (fd != null) {
                float facadeResistance = fd.facadeBlockState().getBlock().getExplosionResistance();
                return Math.max(facadeResistance, defaultResistence);
            }
        }
        return defaultResistence;
    }

}
