package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.blockentity.TestBarrelTankBlockEntity;
import ca.teamdman.sfm.common.containermenu.TestBarrelTankContainerMenu;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.Nullable;

public class TestBarrelTankBlock extends BaseEntityBlock {
    @SFMLocalizationDatagen
    public static final LocalizationEntry TEST_BARREL_TANK_BLOCK = new LocalizationEntry(
            () -> SFMBlocks.TEST_BARREL_TANK.get().getDescriptionId(),
            () -> "Test Barrel Tank"
    );

    public TestBarrelTankBlock(BlockBehaviour.Properties properties) {

        super(properties.sound(SoundType.WOOD).strength(2.5F).sound(SoundType.WOOD));
    }

    @Override
    protected MapCodec<WaterTankBlock> codec() {
        throw new NotImplementedException("This isn't used until 1.20.5 apparently");
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {

        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(
            BlockPos pPos,
            BlockState pState
    ) {

        return SFMBlockEntities.TEST_BARREL_TANK.get().create(pPos, pState);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState pState,
            Level pLevel,
            BlockPos pPos,
            Player pPlayer,
            BlockHitResult pHitResult
    ) {

        if (pLevel.getBlockEntity(pPos) instanceof TestBarrelTankBlockEntity blockEntity) {
            pPlayer.openMenu(new SimpleMenuProvider(
                    (containerId, playerInventory, player) ->
                            new TestBarrelTankContainerMenu(
                                    containerId,
                                    playerInventory,
                                    blockEntity
                            ),
                    blockEntity.getDisplayName()
            ));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

}
