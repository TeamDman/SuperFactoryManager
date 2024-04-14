package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.registry.SFMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;

public class BatteryBlock extends Block implements EntityBlock {
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 10);

    public BatteryBlock() {
        super(BlockBehaviour.Properties.of().strength(5.0F, 6.0F));
        this.registerDefaultState(this.getStateDefinition().any().setValue(LEVEL, 0));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return SFMBlockEntities.BATTERY_BLOCK_ENTITY
                .get()
                .create(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(
            BlockState pState,
            Level pLevel,
            BlockPos pPos,
            Player pPlayer,
            InteractionHand pHand,
            BlockHitResult pHit
    ) {
        var cap = pLevel.getCapability(Capabilities.EnergyStorage.BLOCK, pPos, pHit.getDirection());
        if (cap != null) {
            if (pPlayer.isShiftKeyDown()) {
                cap.extractEnergy(1000, false);
            } else {
                cap.receiveEnergy(1000, false);
            }
            SFM.LOGGER.info("Energy stored: {}", cap.getEnergyStored());
        }
        return InteractionResult.SUCCESS;
    }
}
